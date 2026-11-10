package pzcloud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Verified immutable snapshots and recoverable directory transactions. */
public final class ArchiveEngine {
    static final String MANIFEST="pzcloud-manifest.json";
    static final long MAX_BYTES=256L*1024*1024*1024;
    private final GameGuard guard;
    private final Consumer<String> progress;
    public ArchiveEngine(GameGuard guard, Consumer<String> progress) { this.guard=guard; this.progress=progress; }
    public record Snapshot(Path archive, Map<String,Object> manifest, String sha256, long bytes, String md5) {
        public Snapshot(Path archive,Map<String,Object> manifest,String sha256,long bytes) { this(archive,manifest,sha256,bytes,""); }
        public String get(String key) { return Json.str(manifest,key); }
        public String id() { return get("snapshotId"); }
        public String toString() { return get("createdAt").replace('T',' ').replace("Z","")+" · "+get("reason")+" · "+FilesEx.human(bytes); }
    }
    private record Item(String path, long size, long modified, String sha256) {
        Map<String,Object> json() { return Map.of("path",path,"size",size,"modified",modified,"sha256",sha256); }
    }
    private record Scan(List<Item> files, List<String> directories, long total, String fingerprint) {}
    private Scan scan(Path root) throws IOException {
        TransferGate gate=TransferGate.whileGameClosed(guard);
        List<Item> files=new ArrayList<>(); List<String> dirs=new ArrayList<>(); Set<String> names=new HashSet<>(); long total=0;
        try(var walk=Files.walk(root)) {
            for(Path p:walk.sorted().toList()) {
                if(p.equals(root)) continue; gate.check();
                String name=root.relativize(p).toString().replace('\\','/'); FilesEx.safeRelative(name);
                if(!names.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Archivos que solo difieren en mayúsculas: "+name);
                BasicFileAttributes a=Files.readAttributes(p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(a.isSymbolicLink()||a.isOther()||!p.toRealPath().equals(p.toAbsolutePath().normalize())) throw new IOException("No se admiten enlaces o junctions: "+name);
                if(a.isDirectory()) { dirs.add(name); continue; }
                if(!a.isRegularFile()) throw new IOException("Archivo especial: "+name);
                total=Math.addExact(total,a.size());
                if(total>MAX_BYTES) throw new IOException("La partida supera el límite de 256 GB.");
                files.add(new Item(name,a.size(),a.lastModifiedTime().toMillis(),FilesEx.hash(p,"SHA-256")));
                if(files.size()>2_000_000) throw new IOException("Demasiados archivos.");
            }
        }
        MessageDigest d=FilesEx.digest("SHA-256");
        for(Item f:files) d.update((f.path+"\0"+f.size+"\0"+f.sha256+"\n").getBytes(StandardCharsets.UTF_8));
        for(String dir:dirs) d.update(("dir\0"+dir+"\n").getBytes(StandardCharsets.UTF_8));
        return new Scan(files,dirs,total,HexFormat.of().formatHex(d.digest()));
    }
    public String fingerprint(Path root) throws IOException { return scan(root.toRealPath()).fingerprint; }
    public Snapshot create(Path source, Path outputRoot, Map<String,Object> metadata) throws IOException {
        guard.requireClosed(); Path root=source.toRealPath();
        if(!root.equals(source.toAbsolutePath().normalize())) throw new IOException("La partida no puede estar en un enlace o junction.");
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS)) throw new IOException("No existe la partida.");
        FilesEx.separate(root,outputRoot); Files.createDirectories(outputRoot); outputRoot=outputRoot.toRealPath();
        progress.accept("Leyendo y verificando la partida…"); Scan before=scan(root);
        if(before.files.isEmpty()) throw new IOException("La carpeta de la partida está vacía.");
        requireSpace(outputRoot,before.total+16L*1024*1024);
        Map<String,Object> manifest=new LinkedHashMap<>(metadata); String id=UUID.randomUUID().toString();
        manifest.put("schema",1L); manifest.put("snapshotId",id); manifest.put("createdAt",Instant.now().toString());
        manifest.put("files",before.files.stream().map(Item::json).toList()); manifest.put("directories",before.directories);
        manifest.put("totalBytes",before.total); manifest.put("fingerprint",before.fingerprint);
        Path temp=Files.createTempFile(outputRoot,".snapshot-",".part"), output=outputRoot.resolve(id+".zip");
        try {
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
                zip.setLevel(1); zip.putNextEntry(new ZipEntry(MANIFEST)); zip.write(Json.encode(manifest).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                for(String dir:before.directories) { zip.putNextEntry(new ZipEntry("save/"+dir+"/")); zip.closeEntry(); }
                int count=0; TransferGate gate=TransferGate.whileGameClosed(guard);
                for(Item item:before.files) {
                    if((count++%250)==0) progress.accept("Comprimiendo "+count+" / "+before.files.size()+" archivos…");
                    ZipEntry entry=new ZipEntry("save/"+item.path); entry.setTime(item.modified); zip.putNextEntry(entry);
                    MessageDigest d=FilesEx.digest("SHA-256"); long n=0;
                    try(InputStream in=Files.newInputStream(root.resolve(item.path))) {
                        byte[] b=FilesEx.buffer(); int read;
                        while((read=in.read(b))!=-1) { gate.check(); n+=read; d.update(b,0,read); zip.write(b,0,read); }
                    }
                    zip.closeEntry();
                    if(n!=item.size || !HexFormat.of().formatHex(d.digest()).equals(item.sha256)) throw new IOException("La partida cambió durante el respaldo. Volvé a intentarlo con el juego cerrado.");
                }
            }
            guard.requireClosed();
            if(!scan(root).fingerprint.equals(before.fingerprint)) throw new IOException("La partida cambió durante el respaldo.");
            Snapshot verified=verify(temp,null);
            try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)) { channel.force(true); }
            FilesEx.move(temp,output,false);
            return new Snapshot(output,verified.manifest,verified.sha256,verified.bytes,verified.md5);
        } finally { Files.deleteIfExists(temp); }
    }
    public Snapshot inspect(Path archive) throws IOException {
        try(ZipFile zip=new ZipFile(archive.toFile())) {
            ZipEntry entry=zip.getEntry(MANIFEST);
            if(entry==null||entry.getSize()<0||entry.getSize()>64*1024*1024) throw new IOException("El ZIP no es un respaldo de PZ Cloud Management.");
            Map<String,Object> m;
            try(InputStream in=zip.getInputStream(entry)) { m=Json.object(Json.parse(new String(in.readNBytes(64*1024*1024+1),StandardCharsets.UTF_8))); }
            if(Json.number(m,"schema")!=1) throw new IOException("Formato de respaldo no compatible.");
            try { UUID.fromString(Json.str(m,"snapshotId")); UUID.fromString(Json.str(m,"worldId")); Instant.parse(Json.str(m,"createdAt")); }
            catch(IllegalArgumentException|java.time.format.DateTimeParseException e) { throw new IOException("Manifiesto inválido.",e); }
            FilesEx.safeName(Json.str(m,"mode")); FilesEx.safeName(Json.str(m,"name"));
            return new Snapshot(archive,m,"",Files.size(archive));
        }
    }
    public Snapshot verify(Path archive, String expectedSha) throws IOException { return extract(archive,null,expectedSha); }
    private Snapshot extract(Path archive, Path output, String expectedSha) throws IOException {
        progress.accept("Verificando integridad del respaldo…"); FilesEx.Hashes hashes=FilesEx.hashes(archive); String sha=hashes.sha256();
        if(expectedSha!=null && !expectedSha.isBlank() && !sha.equals(expectedSha)) throw new IOException("El SHA-256 del respaldo no coincide. Descarga dañada.");
        Snapshot snap=inspect(archive); Map<String,Object> m=snap.manifest; List<Object> entries=Json.array(m.get("files"));
        if(entries.isEmpty()||entries.size()>2_000_000) throw new IOException("Lista de archivos inválida.");
        Map<String,Map<String,Object>> expected=new LinkedHashMap<>(); Set<String> folded=new HashSet<>(); Set<String> dirs=new LinkedHashSet<>(); long total=0;
        for(Object v:Json.array(m.get("directories"))) {
            if(!(v instanceof String dir)) throw new IOException("Invalid directory");
            FilesEx.safeRelative(dir); if(!folded.add(dir.toLowerCase(Locale.ROOT))) throw new IOException("Rutas duplicadas."); dirs.add(dir);
        }
        for(Object v:entries) {
            Map<String,Object> f=Json.object(v); String path=Json.str(f,"path"); FilesEx.safeRelative(path);
            if(!folded.add(path.toLowerCase(Locale.ROOT))) throw new IOException("Rutas duplicadas.");
            long size=Json.number(f,"size");
            if(size<0||size>MAX_BYTES||total>MAX_BYTES-size) throw new IOException("Tamaño del respaldo inválido."); total+=size;
            if(!Json.str(f,"sha256").matches("[0-9a-f]{64}")) throw new IOException("Checksum inválido."); expected.put(path,f);
        }
        if(total!=Json.number(m,"totalBytes")) throw new IOException("Tamaño total inconsistente.");
        Set<String> fileFolded=new HashSet<>(); expected.keySet().forEach(p->fileFolded.add(p.toLowerCase(Locale.ROOT)));
        for(String path:folded) {
            int slash=path.lastIndexOf('/');
            while(slash>=0) { if(fileFolded.contains(path.substring(0,slash))) throw new IOException("Archivo usado como directorio."); slash=path.lastIndexOf('/',slash-1); }
        }
        if(output!=null) { requireSpace(output.getParent(),total+16L*1024*1024); Files.createDirectories(output); }
        try(ZipFile zip=new ZipFile(archive.toFile())) {
            Set<String> seen=new HashSet<>(); Enumeration<? extends ZipEntry> enumeration=zip.entries();
            while(enumeration.hasMoreElements()) {
                ZipEntry entry=enumeration.nextElement(); String name=entry.getName();
                if(!seen.add(name)) throw new IOException("Entrada ZIP duplicada."); if(name.equals(MANIFEST)) continue;
                if(!name.startsWith("save/")) throw new IOException("Entrada inesperada en el ZIP."); String rel=name.substring(5);
                if(entry.isDirectory()) {
                    rel=rel.substring(0,rel.length()-1); FilesEx.safeRelative(rel);
                    if(!dirs.contains(rel)) throw new IOException("Directorio inesperado."); if(output!=null) Files.createDirectories(output.resolve(rel)); continue;
                }
                Map<String,Object> f=expected.get(rel); if(f==null) throw new IOException("Archivo inesperado en el ZIP.");
                long size=Json.number(f,"size"); if(entry.getSize()!=size) throw new IOException("Tamaño ZIP inconsistente.");
                MessageDigest d=FilesEx.digest("SHA-256"); CRC32 crc=new CRC32(); long count=0; OutputStream target=OutputStream.nullOutputStream();
                if(output!=null) { Path p=output.resolve(rel); Files.createDirectories(p.getParent()); target=Files.newOutputStream(p,StandardOpenOption.CREATE_NEW); }
                try(InputStream in=zip.getInputStream(entry); OutputStream out=target) {
                    byte[] b=FilesEx.buffer(); int n;
                    while((n=in.read(b))!=-1) { if(count>size-n) throw new IOException("Contenido expandido mayor que el declarado."); count+=n; d.update(b,0,n); crc.update(b,0,n); out.write(b,0,n); }
                }
                if(count!=size||crc.getValue()!=entry.getCrc()||!HexFormat.of().formatHex(d.digest()).equals(Json.str(f,"sha256"))) throw new IOException("Respaldo corrupto: "+rel);
                if(output!=null) Files.setLastModifiedTime(output.resolve(rel),java.nio.file.attribute.FileTime.fromMillis(Json.number(f,"modified")));
            }
            if(seen.size()!=expected.size()+dirs.size()+1) throw new IOException("Faltan archivos o directorios en el ZIP.");
        }
        return new Snapshot(archive,m,sha,snap.bytes,hashes.md5());
    }
    public record RestoreResult(Path target, Path previous, Path journal) {}
    public RestoreResult restore(Path archive, Path savesRoot, String mode, String name, String expectedSha) throws IOException {
        guard.requireClosed(); FilesEx.safeName(mode); FilesEx.safeName(name); Snapshot snap=verify(archive,expectedSha);
        Path root=FilesEx.canonical(savesRoot); Files.createDirectories(root); Path modeDir=root.resolve(mode);
        if(Files.isSymbolicLink(modeDir)) throw new IOException("El destino no puede ser un enlace."); Files.createDirectories(modeDir);
        if(!modeDir.toRealPath().equals(modeDir)) throw new IOException("El destino no puede contener junctions.");
        Path target=modeDir.resolve(name); String tx=UUID.randomUUID().toString();
        Path stage=modeDir.resolve(".pzcloud-stage-"+tx), previous=modeDir.resolve(".pzcloud-previous-"+tx), journal=modeDir.resolve(".pzcloud-restore-"+tx+".json");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&(!Files.isDirectory(target,LinkOption.NOFOLLOW_LINKS)||!target.toRealPath().equals(target))) throw new IOException("El destino no es una carpeta normal.");
        FilesEx.separate(archive,target);
        Map<String,Object> state=new LinkedHashMap<>(); state.put("target",name); state.put("stage",stage.getFileName().toString()); state.put("previous",previous.getFileName().toString()); state.put("snapshotId",snap.id());
        boolean moved=false,installed=false;
        try {
            extract(archive,stage,snap.sha256); guard.requireClosed(); state.put("phase","prepared"); FilesEx.json(journal,state);
            if(Files.exists(target)) { FilesEx.move(target,previous,false); moved=true; }
            state.put("phase","previous_moved"); FilesEx.json(journal,state); FilesEx.move(stage,target,false); installed=true;
            state.put("phase","complete"); FilesEx.json(journal,state); return new RestoreResult(target,moved?previous:null,journal);
        } catch(IOException failure) {
            if(moved&&!installed&&!Files.exists(target)) { try { FilesEx.move(previous,target,false); } catch(IOException rollback) { failure.addSuppressed(rollback); } }
            throw failure;
        } finally { if(Files.exists(stage)) FilesEx.deleteTree(stage); }
    }
    /** Recover validated journals without overwriting any existing target. */
    public void recover(Path savesRoot) throws IOException {
        guard.requireClosed(); if(!Files.isDirectory(savesRoot)) return;
        try(var modes=Files.list(savesRoot)) {
            for(Path mode:modes.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).toList()) {
                if(!mode.toRealPath().equals(mode.toAbsolutePath().normalize())) continue;
                try(var files=Files.newDirectoryStream(mode,".pzcloud-restore-*.json")) {
                    for(Path journal:files) {
                        Map<String,Object> m=FilesEx.readJson(journal);
                        if(!Set.of("prepared","previous_moved").contains(Json.str(m,"phase"))) continue;
                        String name=Json.str(m,"target"), old=Json.str(m,"previous"); FilesEx.safeName(name); FilesEx.safeName(old);
                        if(!old.matches("\\.pzcloud-previous-[0-9a-f-]{36}")) throw new IOException("Journal inválido."); Path target=mode.resolve(name), previous=mode.resolve(old);
                        if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)&&Files.isDirectory(previous,LinkOption.NOFOLLOW_LINKS)&&previous.toRealPath().equals(previous.toAbsolutePath().normalize())) {
                            FilesEx.move(previous,target,false); m.put("phase","recovered"); FilesEx.json(journal,m); progress.accept("Se recuperó la partida anterior: "+name);
                        } else if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)) { m.put("phase","recovered"); FilesEx.json(journal,m); }
                    }
                }
            }
        }
    }
    static void requireSpace(Path path,long needed) throws IOException {
        if(Files.getFileStore(path).getUsableSpace()<needed) throw new IOException("Espacio insuficiente: se necesitan "+FilesEx.human(needed)+".");
    }
}
