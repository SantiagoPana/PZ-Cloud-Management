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
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** System-browser OAuth desktop flow; PKCE/state/loopback, cancellation, publisher configuration. */
public final class GoogleAuth {
    static final String SCOPE="https://www.googleapis.com/auth/drive.appdata";
    @FunctionalInterface interface ClientSource { Map<String,Object> load() throws IOException; }
    private final TokenVault vault;
    private final URI tokenEndpoint;
    private final Duration loginTimeout;
    private final Map<String,Object> applicationClient;
    private final String configurationDiagnostic;
    private final AtomicReference<Attempt> connecting=new AtomicReference<>();
    private Map<String,Object> token;
    private HttpClient http;
    private String sessionProblem="";
    private static final class Attempt {
        final AtomicReference<CompletableFuture<?>> exchange=new AtomicReference<>();
        final CompletableFuture<String> code=new CompletableFuture<>();
        // 0 waiting/exchanging, 1 cancelled, 2 committing, 3 complete.
        final AtomicInteger phase=new AtomicInteger();
    }
    public GoogleAuth(Path home) throws IOException { this(home,URI.create("https://oauth2.googleapis.com/token")); }
    GoogleAuth(Path home,URI endpoint) throws IOException { this(home,endpoint,Duration.ofMinutes(5),GoogleClientDefaults::load); }
    GoogleAuth(Path home,URI endpoint,Duration timeout,ClientSource defaults) throws IOException {
        vault=new TokenVault(home.resolve("google-session.token"));
        tokenEndpoint=endpoint; loginTimeout=timeout;
        Map<String,Object> bundled; String diagnostic="Configuración incluida y válida";
        try { var document=defaults.load(); if(document.isEmpty()) diagnostic="PG001: esta instalación no incluye la conexión de Google"; bundled=Map.copyOf(validateClient(document)); }
        catch(IOException ex) { bundled=Map.of(); if(!diagnostic.startsWith("PG001")) diagnostic="PG002: no se pudo leer una configuración de Google válida"; }
        applicationClient=bundled;
        configurationDiagnostic=diagnostic;
        try { token=vault.read(); } catch(IOException ex) { token=new LinkedHashMap<>(); sessionProblem="La sesión anterior necesita volver a conectarse."; }
        if(!Json.str(token,"refresh_token").isBlank()&&!Json.str(token,"client_id").equals(Json.str(applicationClient,"client_id"))) sessionProblem="Volvé a iniciar sesión para usar la conexión de esta versión de Knox Net.";
    }
    synchronized HttpClient http() { if(http==null) http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build(); return http; }
    public synchronized String connectionId() { return HexFormat.of().formatHex(FilesEx.digest("SHA-256").digest((Json.str(token,"client_id")+"|"+Json.str(token,"refresh_token")).getBytes(StandardCharsets.UTF_8))); }
    public synchronized boolean connected() { return !applicationClient.isEmpty()&&!Json.str(token,"refresh_token").isBlank()&&Json.str(token,"client_id").equals(Json.str(applicationClient,"client_id")); }
    public boolean isConnecting() { return connecting.get()!=null; }
    public boolean configured() { try { client(); return true; } catch(IOException ex) { return false; } }
    public String sessionProblem() { return sessionProblem; }
    public String configurationDiagnostic() { return configurationDiagnostic; }
    public String configurationProblem() {
        try { client(); return ""; }
        catch(IOException ex) { return "Google no está disponible en esta edición. Actualizá Knox Net para conectar tu cuenta. Tus copias locales siguen disponibles."; }
    }
    private Map<String,Object> client() throws IOException {
        if(applicationClient.isEmpty()) throw new IOException("La conexión de Google de esta edición no está disponible. Actualizá Knox Net.");
        return applicationClient;
    }
    private Map<String,Object> validateClient(Map<String,Object> document) throws IOException {
        if(!document.containsKey("installed")) throw new IOException("La conexión de Google de esta edición no está disponible.");
        Map<String,Object> client=Json.object(document.get("installed"));
        String id=Json.str(client,"client_id");
        if(id.isBlank()||id.length()>256||!id.endsWith(".apps.googleusercontent.com")||Json.str(client,"client_secret").length()>4096) throw new IOException("La conexión de Google de la app no es válida.");
        return client;
    }
    /** Returns false only after the session has reached its final, non-cancellable commit. */
    public boolean cancelConnection() {
        Attempt attempt=connecting.get(); if(attempt==null) return false;
        if(!attempt.phase.compareAndSet(0,1)) return attempt.phase.get()==1;
        attempt.code.completeExceptionally(new IOException("Inicio de sesión cancelado."));
        CompletableFuture<?> request=attempt.exchange.getAndSet(null); if(request!=null) request.cancel(true); return true;
    }
    public void connect(Consumer<URI> browser) throws IOException { connect(browser,phase->{}); }
    public void connect(Consumer<URI> browser,Consumer<String> status) throws IOException {
        if(Thread.currentThread().isInterrupted()) throw new IOException("Inicio de sesión cancelado.");
        Attempt attempt=new Attempt();
        if(!connecting.compareAndSet(null,attempt)) throw new IOException("Ya hay un inicio de sesión en curso.");
        HttpServer callback=null;
        try {
            Map<String,Object> client=client();
            String verifier=random(64),state=random(32);
            String challenge=Base64.getUrlEncoder().withoutPadding().encodeToString(FilesEx.digest("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            callback=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            String redirect="http://127.0.0.1:"+callback.getAddress().getPort()+"/callback";
            callback.createContext("/callback",exchange->{
                int code=400; String message="Solicitud inválida. Volvé a Knox Net.",acceptedCode=null; IOException denial=null;
                try {
                    Map<String,String> params;
                    try { params=query(exchange.getRequestURI().getRawQuery()); } catch(IllegalArgumentException ex) { params=Map.of(); }
                    if(exchange.getRequestMethod().equals("GET")&&exchange.getRequestURI().getPath().equals("/callback")&&MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),params.getOrDefault("state","").getBytes(StandardCharsets.UTF_8))) {
                        if(attempt.phase.get()!=0) message="Esta conexión se canceló. Volvé a iniciar sesión desde Knox Net.";
                        else if(params.containsKey("error")) { denial=new IOException("Google canceló o denegó la autorización."); message="Autorización cancelada. Podés volver a Knox Net."; }
                        else if(!params.getOrDefault("code","").isBlank()) { acceptedCode=params.get("code"); code=200; message="La respuesta de Google llegó. Volvé a Knox Net para terminar; podés cerrar esta pestaña."; }
                    }
                    byte[] bytes=message.getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type","text/plain; charset=utf-8"); exchange.getResponseHeaders().set("Cache-Control","no-store"); exchange.sendResponseHeaders(code,bytes.length); exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); if(denial!=null) attempt.code.completeExceptionally(denial); else if(acceptedCode!=null) attempt.code.complete(acceptedCode); }
            });
            callback.start();
            Map<String,String> params=new LinkedHashMap<>();
            params.put("client_id",Json.str(client,"client_id")); params.put("redirect_uri",redirect); params.put("response_type","code"); params.put("scope",SCOPE); params.put("code_challenge",challenge); params.put("code_challenge_method","S256"); params.put("state",state); params.put("access_type","offline"); params.put("prompt","select_account consent");
            if(attempt.phase.get()!=0||Thread.currentThread().isInterrupted()) throw new IOException("Inicio de sesión cancelado.");
            status.accept("waiting"); browser.accept(URI.create("https://accounts.google.com/o/oauth2/v2/auth?"+form(params)));
            String code=attempt.code.get(loginTimeout.toMillis(),TimeUnit.MILLISECONDS);
            if(attempt.phase.get()!=0) throw new IOException("Inicio de sesión cancelado.");
            status.accept("exchanging");
            Map<String,String> body=new LinkedHashMap<>(); body.put("client_id",Json.str(client,"client_id")); body.put("client_secret",Json.str(client,"client_secret")); body.put("redirect_uri",redirect); body.put("code",code); body.put("code_verifier",verifier); body.put("grant_type","authorization_code");
            Map<String,Object> response=tokenRequest(body,attempt);
            if(Json.str(response,"refresh_token").isBlank()) throw new IOException("Google no entregó una sesión renovable. Volvé a iniciar sesión y autorizá el acceso.");
            if(!attempt.phase.compareAndSet(0,2)) throw new IOException("Inicio de sesión cancelado.");
            status.accept("committing"); saveToken(response,Json.str(client,"client_id")); attempt.phase.set(3);
        } catch(TimeoutException ex) { throw new IOException("El inicio de sesión venció. Podés intentarlo otra vez."); }
        catch(ExecutionException ex) { throw new IOException(ex.getCause().getMessage()); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Inicio de sesión cancelado."); }
        finally { attempt.phase.compareAndSet(0,3); if(callback!=null) callback.stop(0); connecting.compareAndSet(attempt,null); }
    }
    public synchronized String accessToken(boolean forceRefresh) throws IOException {
        if(!connected()) throw new IOException("Google Drive no está conectado. Los respaldos quedan pendientes.");
        long expires=token.containsKey("expires_at")?Json.number(token,"expires_at"):0;
        if(!forceRefresh&&expires>Instant.now().getEpochSecond()+60&&!Json.str(token,"access_token").isBlank()) return Json.str(token,"access_token");
        Map<String,Object> client=client();
        if(!Json.str(client,"client_id").equals(Json.str(token,"client_id"))) throw new IOException("La conexión de Google de la app cambió. Volvé a iniciar sesión.");
        Map<String,Object> refreshed=tokenRequest(Map.of("client_id",Json.str(client,"client_id"),"client_secret",Json.str(client,"client_secret"),"refresh_token",Json.str(token,"refresh_token"),"grant_type","refresh_token"));
        refreshed.putIfAbsent("refresh_token",token.get("refresh_token")); saveToken(refreshed,Json.str(client,"client_id")); return Json.str(token,"access_token");
    }
    /** Read-only check of the actual Drive permission; never uploads a test file. */
    public void verifyDriveAccess() throws IOException {
        verifyDriveAccess(URI.create("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&pageSize=1&fields=files(id)"));
    }
    void verifyDriveAccess(URI endpoint) throws IOException {
        for(int attempt=0;attempt<2;attempt++) {
            HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+accessToken(attempt==1)).GET().build();
            CompletableFuture<HttpResponse<byte[]>> pending=http().sendAsync(request,HttpResponse.BodyHandlers.ofByteArray());
            try {
                HttpResponse<byte[]> response=pending.get(20,TimeUnit.SECONDS); byte[] bytes=response.body();
                if(bytes.length>65536) throw new IOException("Google Drive devolvió una respuesta inválida.");
                if(response.statusCode()==401&&attempt==0) continue;
                if(response.statusCode()==200) { Json.array(Json.object(Json.parse(new String(bytes,StandardCharsets.UTF_8))).get("files")); return; }
                String reason=new String(bytes,StandardCharsets.UTF_8);
                if(response.statusCode()==403&&(reason.contains("accessNotConfigured")||reason.contains("SERVICE_DISABLED"))) throw new IOException("Tu sesión está guardada, pero Google Drive no está disponible para Knox Net. Contactá al soporte de la aplicación.");
                if(response.statusCode()==403) throw new IOException("Google no autorizó el acceso a tus respaldos. Volvé a autorizar Drive con Elegir otra cuenta. Si sigue fallando, contactá al soporte de Knox Net.");
                if(response.statusCode()==401) throw new IOException("Google necesita que vuelvas a iniciar sesión. Elegí otra cuenta para autorizar de nuevo.");
                throw new IOException("No se pudo comprobar Google Drive (HTTP "+response.statusCode()+"). La sesión está guardada; podés reintentar.");
            } catch(TimeoutException ex) { throw new IOException("Google Drive tardó demasiado. La sesión está guardada; podés reintentar.",ex); }
            catch(ExecutionException ex) { throw new IOException("No se pudo acceder a Google Drive. Revisá tu conexión a Internet y reintentá.",ex.getCause()); }
            catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Comprobación de Google Drive cancelada.",ex); }
            finally { if(!pending.isDone()) pending.cancel(true); }
        }
    }
    private synchronized void saveToken(Map<String,Object> response,String clientId) throws IOException {
        if(Json.str(response,"access_token").isBlank()) throw new IOException("Google devolvió una respuesta incompleta.");
        Map<String,Object> next=new LinkedHashMap<>(response); next.put("client_id",clientId); next.put("expires_at",Instant.now().getEpochSecond()+Json.number(response,"expires_in")); vault.write(next); token=next; sessionProblem="";
    }
    private Map<String,Object> tokenRequest(Map<String,String> body) throws IOException { return tokenRequest(body,null); }
    private Map<String,Object> tokenRequest(Map<String,String> body,Attempt attempt) throws IOException {
        HttpRequest request=HttpRequest.newBuilder(tokenEndpoint).timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(body))).build();
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            HttpResponse<byte[]> response;
            if(attempt==null) response=http().send(request,HttpResponse.BodyHandlers.ofByteArray());
            else {
                pending=http().sendAsync(request,HttpResponse.BodyHandlers.ofByteArray()); attempt.exchange.set(pending);
                if(attempt.phase.get()!=0) pending.cancel(true);
                response=pending.get();
            }
            if(response.body().length>1024*1024) throw new IOException("Google devolvió una respuesta de conexión demasiado grande.");
            if(response.statusCode()!=200) throw new IOException(response.statusCode()==400?"Google necesita que vuelvas a iniciar sesión.":"No se pudo conectar con Google (HTTP "+response.statusCode()+"). Intentá más tarde.");
            return Json.object(Json.parse(new String(response.body(),StandardCharsets.UTF_8)));
        } catch(CancellationException ex) { throw new IOException("Inicio de sesión cancelado."); }
        catch(ExecutionException ex) { throw new IOException("No se pudo conectar con Google. Revisá tu conexión a Internet.",ex.getCause()); }
        catch(InterruptedException ex) { if(pending!=null) pending.cancel(true); Thread.currentThread().interrupt(); throw new IOException("Inicio de sesión cancelado."); }
        finally { if(attempt!=null&&pending!=null) attempt.exchange.compareAndSet(pending,null); }
    }
    public synchronized void disconnect() throws IOException {
        if(isConnecting()) throw new IOException("Cancelá el inicio de sesión antes de desconectar la cuenta."); vault.delete(); token=new LinkedHashMap<>(); sessionProblem="";
    }
    static String random(int bytes) { byte[] b=new byte[bytes]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static String encode(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    static String form(Map<String,String> values) { StringJoiner join=new StringJoiner("&"); values.forEach((k,v)->join.add(encode(k)+"="+encode(v))); return join.toString(); }
    static Map<String,String> query(String query) {
        Map<String,String> result=new HashMap<>(); if(query==null) return result;
        for(String part:query.split("&")) { String[] pair=part.split("=",2); if(pair.length==2) result.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),URLDecoder.decode(pair[1],StandardCharsets.UTF_8)); }
        return result;
    }
}
