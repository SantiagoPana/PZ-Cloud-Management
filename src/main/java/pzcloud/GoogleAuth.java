package pzcloud;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** OAuth desktop Authorization Code + PKCE S256, random state and loopback callback. */
public final class GoogleAuth {
    static final String SCOPE="https://www.googleapis.com/auth/drive.appdata";
    private final Path clientFile;
    private final TokenVault vault;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();
    private Map<String,Object> token;
    private final URI tokenEndpoint;
    public GoogleAuth(Path home) throws IOException { this(home,URI.create("https://oauth2.googleapis.com/token")); }
    GoogleAuth(Path home,URI endpoint) throws IOException { clientFile=home.resolve("google-client.json"); vault=new TokenVault(home.resolve("google-session.token")); token=vault.read(); tokenEndpoint=endpoint; }
    public synchronized String connectionId() { return HexFormat.of().formatHex(FilesEx.digest("SHA-256").digest((Json.str(token,"client_id")+"|"+Json.str(token,"refresh_token")).getBytes(StandardCharsets.UTF_8))); }
    public synchronized boolean connected() { return !Json.str(token,"refresh_token").isBlank(); }
    public boolean configured() { return Files.exists(clientFile); }
    private Map<String,Object> client() throws IOException {
        if(!configured()) throw new IOException("Importá primero el JSON OAuth de tipo Escritorio. Ver docs/GOOGLE_DRIVE.md.");
        return validateClient(FilesEx.readJson(clientFile));
    }
    private Map<String,Object> validateClient(Map<String,Object> json) throws IOException {
        Map<String,Object> c=Json.object(json.get("installed"));
        if(!Json.str(c,"client_id").endsWith(".apps.googleusercontent.com")) throw new IOException("Se necesita un cliente OAuth de tipo Aplicación de escritorio.");
        return c;
    }
    public synchronized void importClient(Path input) throws IOException {
        Map<String,Object> doc=FilesEx.readJson(input); Map<String,Object> c=validateClient(doc);
        if(connected()&&!Json.str(token,"client_id").equals(Json.str(c,"client_id"))) throw new IOException("Desconectá la cuenta antes de cambiar el cliente OAuth.");
        FilesEx.json(clientFile,doc);
    }
    public void connect(Consumer<URI> openBrowser) throws IOException {
        Map<String,Object> c=client(); String verifier=random(64),state=random(32);
        String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(FilesEx.digest("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        CompletableFuture<String> result=new CompletableFuture<>();
        HttpServer callback=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String redirect="http://127.0.0.1:"+callback.getAddress().getPort()+"/callback";
        callback.createContext("/callback",exchange -> {
            int status=400; String message="Solicitud inválida. Volvé a PZ Cloud Management.";
            try {
                Map<String,String> params=query(exchange.getRequestURI().getRawQuery());
                if(exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getPath().equals("/callback") && MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),params.getOrDefault("state","").getBytes(StandardCharsets.UTF_8))) {
                    if(params.containsKey("error")) { result.completeExceptionally(new IOException("Google: autorización cancelada o denegada.")); message="Autorización cancelada. Podés cerrar esta pestaña."; }
                    else if(params.containsKey("code")) { result.complete(params.get("code")); status=200; message="Autorización recibida. Podés cerrar esta pestaña y volver a PZ Cloud Management."; }
                }
                byte[] body=message.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control","no-store");
                exchange.sendResponseHeaders(status,body.length); exchange.getResponseBody().write(body);
            } finally { exchange.close(); }
        });
        callback.start();
        try {
            String params=form(Map.of("client_id",Json.str(c,"client_id"),"redirect_uri",redirect,"response_type","code","scope",SCOPE,"code_challenge",challenge,"code_challenge_method","S256","state",state,"access_type","offline","prompt","consent"));
            openBrowser.accept(URI.create("https://accounts.google.com/o/oauth2/v2/auth?"+params));
            String code=result.get(180,TimeUnit.SECONDS);
            Map<String,String> body=new LinkedHashMap<>(); body.put("client_id",Json.str(c,"client_id")); body.put("client_secret",Json.str(c,"client_secret"));
            body.put("redirect_uri",redirect); body.put("code",code); body.put("code_verifier",verifier); body.put("grant_type","authorization_code");
            Map<String,Object> response=tokenRequest(body);
            synchronized(this) {
                if(Json.str(response,"refresh_token").isBlank()) throw new IOException("Google no entregó una sesión renovable. Reconectá autorizando el acceso.");
                saveToken(response,Json.str(c,"client_id"));
            }
        } catch(TimeoutException e) { throw new IOException("La conexión con Google venció. Intentá nuevamente."); }
        catch(ExecutionException e) { throw new IOException(e.getCause().getMessage()); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Conexión cancelada.",e); }
        finally { callback.stop(0); }
    }
    public synchronized String accessToken(boolean forceRefresh) throws IOException {
        if(!connected()) throw new IOException("Google Drive no está conectado. Los respaldos quedan pendientes.");
        long expires=token.containsKey("expires_at")?Json.number(token,"expires_at"):0;
        if(!forceRefresh&&expires>Instant.now().getEpochSecond()+60&&!Json.str(token,"access_token").isBlank()) return Json.str(token,"access_token");
        Map<String,Object> c=client();
        if(!Json.str(c,"client_id").equals(Json.str(token,"client_id"))) throw new IOException("El cliente OAuth cambió. Reconectá Google Drive.");
        Map<String,Object> refreshed=tokenRequest(Map.of("client_id",Json.str(c,"client_id"),"client_secret",Json.str(c,"client_secret"),"refresh_token",Json.str(token,"refresh_token"),"grant_type","refresh_token"));
        refreshed.putIfAbsent("refresh_token",token.get("refresh_token")); saveToken(refreshed,Json.str(c,"client_id"));
        return Json.str(token,"access_token");
    }
    private void saveToken(Map<String,Object> response,String clientId) throws IOException {
        if(Json.str(response,"access_token").isBlank()) throw new IOException("Respuesta de autorización incompleta.");
        Map<String,Object> next=new LinkedHashMap<>(response); next.put("client_id",clientId); next.put("expires_at",Instant.now().getEpochSecond()+Json.number(response,"expires_in"));
        vault.write(next); token=next;
    }
    private Map<String,Object> tokenRequest(Map<String,String> body) throws IOException {
        HttpRequest request=HttpRequest.newBuilder(tokenEndpoint).timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(body))).build();
        try {
            HttpResponse<InputStream> res=http.send(request,HttpResponse.BodyHandlers.ofInputStream()); String text;
            try(InputStream in=res.body()) { text=new String(in.readNBytes(1024*1024),StandardCharsets.UTF_8); }
            if(res.statusCode()!=200) throw new IOException(res.statusCode()==400?"La sesión de Google venció o fue revocada. Volvé a conectar Drive.":"Google no pudo autorizar la sesión (HTTP "+res.statusCode()+").");
            return Json.object(Json.parse(text));
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Conexión cancelada.",e); }
    }
    public synchronized void disconnect() throws IOException { vault.delete(); token=new LinkedHashMap<>(); }
    static String random(int bytes) { byte[] b=new byte[bytes]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static String encode(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    static String form(Map<String,String> values) { StringJoiner j=new StringJoiner("&"); values.forEach((k,v)->j.add(encode(k)+"="+encode(v))); return j.toString(); }
    static Map<String,String> query(String query) {
        Map<String,String> out=new HashMap<>(); if(query==null) return out;
        for(String part:query.split("&")) { String[] kv=part.split("=",2); if(kv.length==2) out.put(URLDecoder.decode(kv[0],StandardCharsets.UTF_8),URLDecoder.decode(kv[1],StandardCharsets.UTF_8)); }
        return out;
    }
}
