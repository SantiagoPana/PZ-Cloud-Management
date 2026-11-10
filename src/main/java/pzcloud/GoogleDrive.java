package pzcloud;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/** Drive v3 appDataFolder; immutable versions, resumable bounded chunks, persistent session. */
public final class GoogleDrive implements CloudProvider {
    @FunctionalInterface interface Tokens { String get(boolean refresh) throws IOException; }
    private final Tokens tokens;
    private final URI api,uploadApi;
    private final Path sessions;
    private final Consumer<String> progress;
    private final java.util.function.Supplier<String> connection;
    private TransferGate gate=TransferGate.unrestricted();
    private HttpClient http;
    private synchronized HttpClient http() { if(http==null) http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build(); return http; }
    private static final int CHUNK=4*1024*1024;
    private static final String FIELDS="id,name,size,md5Checksum,appProperties";
    public GoogleDrive(GoogleAuth auth,Path home,Consumer<String> progress) throws IOException {
        this(auth::accessToken,URI.create("https://www.googleapis.com/drive/v3/"),URI.create("https://www.googleapis.com/upload/drive/v3/"),home.resolve("sessions"),progress,auth::connectionId); this.http=auth.http();
    }
    public GoogleDrive(GoogleAuth auth,Path home,Consumer<String> progress,TransferGate gate) throws IOException {
        this(auth,home,progress); this.gate=gate;
    }
    GoogleDrive(Tokens tokens,URI api,URI uploadApi,Path sessions,Consumer<String> progress) throws IOException {
        this(tokens,api,uploadApi,sessions,progress,()->"test-connection");
    }
    GoogleDrive(Tokens tokens,URI api,URI uploadApi,Path sessions,Consumer<String> progress,java.util.function.Supplier<String> connection) throws IOException {
        this.connection=connection; this.tokens=tokens; this.api=api; this.uploadApi=uploadApi; this.sessions=sessions; this.progress=progress; Files.createDirectories(sessions);
    }
    private void allowed(URI uri) throws IOException {
        if(!Objects.equals(uri.getScheme(),api.getScheme())||!Objects.equals(uri.getHost(),api.getHost())||uri.getPort()!=api.getPort()||uri.getUserInfo()!=null) throw new IOException("Drive devolvió una dirección de transferencia no válida.");
    }
    private HttpRequest request(URI uri,String method,Map<String,String> headers,HttpRequest.BodyPublisher body,boolean refresh) throws IOException {
        allowed(uri);
        HttpRequest.Builder b=HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3)).header("Authorization","Bearer "+tokens.get(refresh));
        headers.forEach(b::header); return b.method(method,body).build();
    }
    private HttpResponse<byte[]> call(URI uri,String method,Map<String,String> headers,HttpRequest.BodyPublisher body) throws IOException {
        try {
            HttpResponse<byte[]> response=http().send(request(uri,method,headers,body,false),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()==401) response=http().send(request(uri,method,headers,body,true),HttpResponse.BodyHandlers.ofByteArray());
            if(response.body().length>2*1024*1024) throw new IOException("Respuesta de Drive demasiado grande."); return response;
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Transferencia cancelada.",e); }
    }
    private Map<String,Object> json(HttpResponse<byte[]> response) throws IOException {
        check(response); return Json.object(Json.parse(new String(response.body(),StandardCharsets.UTF_8)));
    }
    private void check(HttpResponse<?> res) throws IOException {
        if(res.statusCode()<200||res.statusCode()>=300) throw new IOException("Google Drive: HTTP "+res.statusCode()+". Revisá conexión, autorización y espacio de la cuenta; el respaldo se conserva pendiente.");
    }
    private URI fileUri(String id,String query) throws IOException {
        if(!id.matches("[A-Za-z0-9_-]+")) throw new IOException("ID de Drive inválido."); return api.resolve("files/"+id+query);
    }
    private Remote remote(Map<String,Object> file) throws IOException {
        Map<String,Object> p=file.containsKey("appProperties")?Json.object(file.get("appProperties")):Map.of();
        return new Remote(Json.str(file,"id"),Json.str(file,"name"),Json.number(file,"size"),Json.str(file,"md5Checksum"),p);
    }
    public List<Remote> list() throws IOException {
        List<Remote> out=new ArrayList<>(); String token=""; Set<String> pages=new HashSet<>();
        do {
            String q="trashed = false and appProperties has { key='pzcloud' and value='1' }";
            URI uri=api.resolve("files?spaces=appDataFolder&pageSize=100&fields="+GoogleAuth.encode("nextPageToken,files("+FIELDS+")")+"&q="+GoogleAuth.encode(q)+"&pageToken="+GoogleAuth.encode(token));
            Map<String,Object> data=json(call(uri,"GET",Map.of(),HttpRequest.BodyPublishers.noBody()));
            for(Object o:Json.array(data.get("files"))) out.add(remote(Json.object(o)));
            token=Json.str(data,"nextPageToken");
            if(!token.isEmpty()&&!pages.add(token)) throw new IOException("Paginación de Drive inválida.");
        } while(!token.isEmpty());
        out.sort(Comparator.comparing((Remote r)->r.get("created")).reversed()); return out;
    }
    private Remote existing(String id) throws IOException {
        HttpResponse<byte[]> res=call(fileUri(id,"?fields="+GoogleAuth.encode(FIELDS)),"GET",Map.of(),HttpRequest.BodyPublishers.noBody());
        return res.statusCode()==404?null:remote(json(res));
    }
    public Remote upload(ArchiveEngine.Snapshot snap) throws IOException {
        gate.check(); UUID.fromString(snap.id()); String md5=snap.md5().isBlank()?FilesEx.hash(snap.archive(),"MD5"):snap.md5();
        if(Files.size(snap.archive())!=snap.bytes()||(snap.md5().isBlank()&&!FilesEx.hash(snap.archive(),"SHA-256").equals(snap.sha256()))) throw new IOException("El respaldo local cambió antes de subirlo.");
        Path stateFile=sessions.resolve(snap.id()+".json");
        Map<String,Object> state=Files.exists(stateFile)?FilesEx.readJson(stateFile):new LinkedHashMap<>();
        String connectionId=connection.get();
        if(!Json.str(state,"connection").equals(connectionId)) state=new LinkedHashMap<>();
        state.put("connection",connectionId);
        String id=Json.str(state,"fileId");
        if(id.isBlank()) {
            Remote known=findSnapshot(snap.id());
            if(known!=null) { verifyUploaded(known,snap,md5); state.put("fileId",known.fileId()); state.put("sha256",snap.sha256()); finishSession(stateFile,state); return known; }
            Map<String,Object> ids=json(call(api.resolve("files/generateIds?count=1&space=appDataFolder"),"GET",Map.of(),HttpRequest.BodyPublishers.noBody()));
            id=Json.array(ids.get("ids")).get(0).toString(); state.put("fileId",id); state.put("sha256",snap.sha256()); FilesEx.json(stateFile,state);
        }
        if(!Json.str(state,"sha256").equals(snap.sha256())) throw new IOException("La sesión de subida no corresponde al respaldo.");
        Remote complete=existing(id);
        if(complete!=null) { verifyUploaded(complete,snap,md5); finishSession(stateFile,state); return complete; }
        String session=Json.str(state,"session"); long offset=0;
        if(!session.isBlank()) {
            HttpResponse<byte[]> res=call(URI.create(session),"PUT",Map.of("Content-Range","bytes */"+snap.bytes()),HttpRequest.BodyPublishers.noBody());
            if(res.statusCode()==200||res.statusCode()==201) { complete=remote(json(res)); verifyUploaded(complete,snap,md5); finishSession(stateFile,state); return complete; }
            if(res.statusCode()==308) offset=offset(res,snap.bytes());
            else if(res.statusCode()==404||res.statusCode()==410) session="";
            else { if(res.statusCode()>=400&&res.statusCode()<500) { state.remove("session"); FilesEx.json(stateFile,state); } check(res); throw new IOException("Estado de subida inesperado."); }
        }
        if(session.isBlank()) {
            Map<String,Object> p=new LinkedHashMap<>();
            p.put("pzcloud","1"); p.put("snapshot",snap.id()); p.put("world",snap.get("worldId")); p.put("sha256",snap.sha256());
            p.put("created",snap.get("createdAt")); p.put("device",shortText(snap.get("deviceName"),60)); p.put("mode",shortText(snap.get("mode"),60));
            p.put("saveName",shortText(snap.get("name"),80)); p.put("build",shortText(snap.get("gameBuild"),40)); p.put("parent",snap.get("parentId"));
            Map<String,Object> metadata=Map.of("id",id,"name","pzcloud-"+snap.id()+".zip","mimeType","application/zip","parents",List.of("appDataFolder"),"appProperties",p);
            URI uri=uploadApi.resolve("files?uploadType=resumable&fields="+GoogleAuth.encode(FIELDS));
            HttpResponse<byte[]> res=call(uri,"POST",Map.of("Content-Type","application/json; charset=utf-8","X-Upload-Content-Type","application/zip","X-Upload-Content-Length",Long.toString(snap.bytes())),HttpRequest.BodyPublishers.ofString(Json.encode(metadata)));
            check(res); session=res.headers().firstValue("Location").orElseThrow(()->new IOException("Drive no entregó una sesión de subida.")); allowed(URI.create(session));
            state.put("session",session); FilesEx.json(stateFile,state);
        }
        try(RandomAccessFile in=new RandomAccessFile(snap.archive().toFile(),"r")) {
            int stalled=0;
            while(offset<snap.bytes()) {
                gate.check();
                int length=(int)Math.min(CHUNK,snap.bytes()-offset); byte[] bytes=new byte[length]; in.seek(offset); in.readFully(bytes);
                progress.accept("Subiendo a Google Drive: "+(100*offset/snap.bytes())+"% · "+FilesEx.human(offset)+" / "+FilesEx.human(snap.bytes()));
                HttpResponse<byte[]> res=call(URI.create(session),"PUT",Map.of("Content-Type","application/zip","Content-Range","bytes "+offset+"-"+(offset+length-1)+"/"+snap.bytes()),HttpRequest.BodyPublishers.ofByteArray(bytes));
                if(res.statusCode()==200||res.statusCode()==201) {
                    complete=remote(json(res)); verifyUploaded(complete,snap,md5); finishSession(stateFile,state); progress.accept("Respaldo subido y verificado en Google Drive."); return complete;
                }
                if(res.statusCode()!=308) { if(res.statusCode()>=400&&res.statusCode()<500) { state.remove("session"); FilesEx.json(stateFile,state); } check(res); throw new IOException("Estado de subida inesperado."); }
                long next=offset(res,snap.bytes());
                if(next<=offset) { if(++stalled>2) throw new IOException("La subida no avanzó. Se reintentará más tarde."); } else stalled=0;
                offset=next;
            }
        }
        // A server may acknowledge all bytes with 308; check final metadata on the next retry.
        complete=existing(id); if(complete==null) throw new IOException("Drive todavía no confirmó el respaldo.");
        verifyUploaded(complete,snap,md5); finishSession(stateFile,state); return complete;
    }
    private Remote findSnapshot(String snapshot) throws IOException {
        String q="trashed = false and appProperties has { key='snapshot' and value='"+snapshot+"' }";
        var data=json(call(api.resolve("files?spaces=appDataFolder&fields="+GoogleAuth.encode("files("+FIELDS+")")+"&q="+GoogleAuth.encode(q)),"GET",Map.of(),HttpRequest.BodyPublishers.noBody()));
        for(Object f:Json.array(data.get("files"))) { Remote r=remote(Json.object(f)); if(r.id().equals(snapshot)) return r; } return null;
    }
    private static void finishSession(Path file,Map<String,Object> state) throws IOException { state.remove("session"); FilesEx.json(file,state); }
    private static long offset(HttpResponse<?> response,long size) throws IOException {
        String range=response.headers().firstValue("Range").orElse("");
        if(range.isBlank()) return 0;
        if(!range.matches("bytes=0-[0-9]+")) throw new IOException("Range de Drive inválido.");
        try { long n=Math.addExact(Long.parseLong(range.substring(8)),1); if(n<0||n>size) throw new IOException("Offset inválido."); return n; }
        catch(ArithmeticException|NumberFormatException e) { throw new IOException("Offset inválido."); }
    }
    private static String shortText(String s,int max) {
        while(s.getBytes(StandardCharsets.UTF_8).length>max) s=s.substring(0,s.offsetByCodePoints(s.length(),-1)); return s;
    }
    private static void verifyUploaded(Remote r,ArchiveEngine.Snapshot s,String md5) throws IOException {
        if(r.bytes()!=s.bytes()||!r.md5().equals(md5)||!r.get("sha256").equals(s.sha256())||!r.id().equals(s.id())) throw new IOException("Drive no confirmó la integridad del archivo. Se conserva la copia local.");
    }
    public Path download(Remote r,Path directory) throws IOException {
        UUID.fromString(r.id());
        if(!r.get("sha256").matches("[0-9a-f]{64}")||r.bytes()<=0||r.bytes()>ArchiveEngine.MAX_BYTES+1024L*1024*1024) throw new IOException("Metadatos de descarga inválidos.");
        Files.createDirectories(directory); Path target=directory.resolve(r.id()+".zip"), partial=directory.resolve(r.id()+".download"), chunk=directory.resolve(r.id()+".chunk");
        if(Files.exists(target)&&FilesEx.hash(target,"SHA-256").equals(r.get("sha256"))) return target;
        if(Files.exists(target)) throw new IOException("Ya hay un archivo local con el mismo ID y distinto contenido.");
        long position=Files.exists(partial)?Files.size(partial):0;
        if(position>r.bytes()) { Files.delete(partial); position=0; }
        ArchiveEngine.requireSpace(directory,r.bytes()-position+CHUNK);
        try {
            while(position<r.bytes()) {
                long end=Math.min(r.bytes()-1,position+CHUNK-1); progress.accept("Descargando de Drive: "+100*position/r.bytes()+"%");
                HttpResponse<Path> res;
                try {
                    res=http().send(request(fileUri(r.fileId(),"?alt=media"),"GET",Map.of("Range","bytes="+position+"-"+end),HttpRequest.BodyPublishers.noBody(),false),HttpResponse.BodyHandlers.ofFile(chunk,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING));
                    if(res.statusCode()==401) res=http().send(request(fileUri(r.fileId(),"?alt=media"),"GET",Map.of("Range","bytes="+position+"-"+end),HttpRequest.BodyPublishers.noBody(),true),HttpResponse.BodyHandlers.ofFile(chunk,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING));
                } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Descarga cancelada.",e); }
                check(res); long length=Files.size(chunk);
                if(res.statusCode()==200) {
                    if(length!=r.bytes()) throw new IOException("Descarga incompleta."); FilesEx.move(chunk,partial,true); position=length;
                } else if(res.statusCode()==206) {
                    String expected="bytes "+position+"-"+end+"/"+r.bytes();
                    if(!res.headers().firstValue("Content-Range").orElse("").equals(expected)||length!=end-position+1) throw new IOException("Rango de descarga inconsistente.");
                    try(OutputStream out=Files.newOutputStream(partial,StandardOpenOption.CREATE,StandardOpenOption.APPEND)) { Files.copy(chunk,out); } position+=length;
                } else throw new IOException("Respuesta de descarga inesperada.");
            }
            if(!FilesEx.hash(partial,"SHA-256").equals(r.get("sha256"))) { Files.deleteIfExists(partial); throw new IOException("Descarga corrupta. El respaldo se puede descargar nuevamente."); }
            FilesEx.move(partial,target,false); return target;
        } finally { Files.deleteIfExists(chunk); }
    }
    public void delete(Remote remote) throws IOException {
        check(call(fileUri(remote.fileId(),""),"DELETE",Map.of(),HttpRequest.BodyPublishers.noBody()));
        Files.deleteIfExists(sessions.resolve(remote.id()+".json"));
    }
}
