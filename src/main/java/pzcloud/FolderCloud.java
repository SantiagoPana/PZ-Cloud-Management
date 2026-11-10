package pzcloud;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Uses an existing synced folder. A local copy is never reported as confirmed cloud delivery. */
public final class FolderCloud implements CloudProvider {
    private final Path root;
    private final Consumer<String> progress;
    private final TransferGate gate;
    public FolderCloud(Path folder,Consumer<String> progress) throws IOException { this(folder,progress,TransferGate.unrestricted()); }
    FolderCloud(Path folder,Consumer<String> progress,TransferGate gate) throws IOException {
        root=FilesEx.canonical(folder); this.progress=progress; this.gate=gate;
        if(!Files.isDirectory(root)) throw new IOException("La carpeta sincronizada no está disponible. Revisá que Drive u OneDrive esté abierto.");
    }
    public String deliveredStatus() { return "folder"; }
    private Path payload(String id) throws IOException { UUID.fromString(id); return root.resolve("PZCloud-"+id+".zip"); }
    private Path metadata(String id) throws IOException { UUID.fromString(id); return root.resolve("PZCloud-"+id+".json"); }
    private Remote remote(Map<String,Object> meta) throws IOException {
        if(Json.number(meta,"schema")!=1) throw new IOException("Formato de carpeta no compatible.");
        var props=Json.object(meta.get("properties")); String id=Json.str(props,"snapshot");
        try { UUID.fromString(id); } catch(IllegalArgumentException e) { throw new IOException("ID de copia inválido."); }
        if(!Json.str(props,"sha256").matches("[0-9a-f]{64}")) throw new IOException("Checksum de copia inválido.");
        return new Remote(id,Json.str(meta,"name"),Json.number(meta,"bytes"),Json.str(meta,"md5"),props);
    }
    public List<Remote> list() throws IOException {
        List<Remote> result=new ArrayList<>();
        try(var paths=Files.newDirectoryStream(root,"PZCloud-*.json")) {
            for(Path file:paths) {
                try {
                    if(Files.size(file)>65536||Files.isSymbolicLink(file)) continue;
                    Remote r=remote(FilesEx.readJson(file)); if(!file.getFileName().toString().equals("PZCloud-"+r.id()+".json")) continue; Path zip=payload(r.id());
                    if(Files.isRegularFile(zip,LinkOption.NOFOLLOW_LINKS)&&Files.size(zip)==r.bytes()) result.add(r);
                } catch(IOException|IllegalArgumentException ignored) { /* Incomplete/conflicted sync metadata is not a restorable version. */ }
            }
        }
        result.sort(Comparator.comparing((Remote r)->r.get("created")).reversed()); return result;
    }
    public Remote upload(ArchiveEngine.Snapshot snapshot) throws IOException {
        gate.check(); FilesEx.separate(snapshot.archive().getParent(),root);
        Path zip=payload(snapshot.id()), meta=metadata(snapshot.id());
        if(Files.exists(zip)) {
            if(!FilesEx.hash(zip,"SHA-256").equals(snapshot.sha256())) throw new IOException("Ya hay una copia con el mismo ID y contenido distinto. No se reemplazó.");
        } else copy(snapshot.archive(),zip,snapshot.sha256(),true);
        Map<String,Object> props=new LinkedHashMap<>();
        props.put("snapshot",snapshot.id()); props.put("world",snapshot.get("worldId")); props.put("sha256",snapshot.sha256());
        props.put("created",snapshot.get("createdAt")); props.put("parent",snapshot.get("parentId")); props.put("saveName",snapshot.get("name"));
        props.put("mode",snapshot.get("mode")); props.put("device",snapshot.get("deviceName")); props.put("build",snapshot.get("gameBuild")); props.put("transport","folder");
        Map<String,Object> data=Map.of("schema",1,"name",zip.getFileName().toString(),"bytes",snapshot.bytes(),"md5",snapshot.md5(),"properties",props);
        FilesEx.json(meta,data); progress.accept("Copia lista en tu carpeta. Drive u OneDrive debe terminar de sincronizarla."); return remote(data);
    }
    public Path download(Remote remote,Path directory) throws IOException {
        Files.createDirectories(directory); FilesEx.separate(directory,root); Path target=directory.resolve(remote.id()+".zip");
        if(Files.exists(target)) {
            if(!FilesEx.hash(target,"SHA-256").equals(remote.get("sha256"))) throw new IOException("La copia local tiene contenido distinto. Importá el respaldo con otro ID.");
            return target;
        }
        copy(payload(remote.id()),target,remote.get("sha256"),false); return target;
    }
    private void copy(Path source,Path target,String expected,boolean pauseWhilePlaying) throws IOException {
        if(Files.isSymbolicLink(source)||!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)) throw new IOException("La copia todavía no está disponible en esta PC.");
        ArchiveEngine.requireSpace(target.getParent(),Files.size(source)+1024*1024);
        Path temp=Files.createTempFile(target.getParent(),".pzcloud-copy-",".part");
        try {
            var digest=FilesEx.digest("SHA-256"); long count=0,nextCheck=0;
            try(InputStream in=Files.newInputStream(source); OutputStream out=new BufferedOutputStream(Files.newOutputStream(temp))) {
                byte[] buffer=new byte[128*1024]; int n;
                while((n=in.read(buffer))!=-1) {
                    if(count>=nextCheck) { if(pauseWhilePlaying) gate.check(); nextCheck=count+4*1024*1024; }
                    digest.update(buffer,0,n); out.write(buffer,0,n); count+=n;
                }
            }
            if(!HexFormat.of().formatHex(digest.digest()).equals(expected)) throw new IOException("La carpeta aún no terminó de sincronizar esta copia o su contenido está dañado. Intentá más tarde.");
            FilesEx.move(temp,target,false);
        } finally { Files.deleteIfExists(temp); }
    }
    public void delete(Remote remote) throws IOException {
        // Remove the commit marker first; another PC must never discover a partially deleted copy.
        Files.deleteIfExists(metadata(remote.id())); Files.deleteIfExists(payload(remote.id()));
    }
}
