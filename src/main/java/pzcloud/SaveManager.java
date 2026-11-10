package pzcloud;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** All operations run on one worker and share the application's exclusive process lock. */
public final class SaveManager {
    public final Config config;
    public final ArchiveEngine engine;
    public final GameGuard guard;
    public final GoogleAuth auth;
    public final CloudProvider cloud;
    private final Consumer<String> log;
    private final Set<String> restorePins=new HashSet<>();
    public SaveManager(Config config,Consumer<String> log) throws IOException {
        this(config,new GameGuard(),null,log);
    }
    SaveManager(Config config,GameGuard guard,CloudProvider cloud,Consumer<String> log) throws IOException {
        this.config=config; this.guard=guard; this.log=log; engine=new ArchiveEngine(guard,log); auth=new GoogleAuth(config.home);
        this.cloud=cloud==null?new GoogleDrive(auth,config.home,log):cloud;
        FilesEx.separate(config.saves(),config.home); Files.createDirectories(config.archives());
    }
    public record Local(ArchiveEngine.Snapshot snapshot,Map<String,Object> state) {
        public String status() { return Json.str(state,"status"); }
        public String toString() { return snapshot+" · "+switch(status()) { case "synced"->"En la nube"; case "pending"->"Pendiente de subir"; default->"Local"; }; }
    }
    public List<Config.World> worlds() throws IOException {
        List<Config.World> out=new ArrayList<>(); if(!Files.isDirectory(config.saves())) return out;
        try(var modes=Files.list(config.saves())) {
            for(Path mode:modes.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&!p.getFileName().toString().startsWith(".")).sorted().toList()) {
                String type=mode.getFileName().toString();
                // Host/server backups also need Server configuration and account data: do not pretend SP snapshots cover them.
                if(type.equalsIgnoreCase("Multiplayer")) continue;
                try(var saves=Files.list(mode)) {
                    for(Path save:saves.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)&&!p.getFileName().toString().startsWith(".")).sorted().toList()) out.add(config.world(type,save.getFileName().toString()));
                }
            }
        }
        return out;
    }
    private Path metadata(String id) { return config.archives().resolve(id+".meta.json"); }
    private void register(ArchiveEngine.Snapshot snap,String status,String remoteId) throws IOException {
        Map<String,Object> state=new LinkedHashMap<>(); state.put("status",status); state.put("remoteId",remoteId); state.put("sha256",snap.sha256()); state.put("lastError","");
        FilesEx.json(metadata(snap.id()),state);
    }
    public synchronized List<Local> history(Config.World world) throws IOException {
        List<Local> out=new ArrayList<>();
        try(var files=Files.newDirectoryStream(config.archives(),"*.zip")) {
            for(Path p:files) {
                try {
                    ArchiveEngine.Snapshot snap=engine.inspect(p);
                    if(!p.getFileName().toString().equals(snap.id()+".zip")) throw new IOException("Nombre de respaldo no válido.");
                    Path meta=metadata(snap.id());
                    if(!Files.exists(meta)) { snap=engine.verify(p,null); register(snap,snap.get("destination").equals("both")||snap.get("destination").equals("drive")?"pending":"local",""); }
                    Map<String,Object> state=FilesEx.readJson(meta);
                    if(!Json.str(state,"sha256").matches("[0-9a-f]{64}")) throw new IOException("Checksum local ausente o inválido.");
                    snap=new ArchiveEngine.Snapshot(p,snap.manifest(),Json.str(state,"sha256"),snap.bytes());
                    if(world==null||(snap.get("mode").equals(world.mode)&&snap.get("name").equals(world.name))) out.add(new Local(snap,state));
                } catch(IOException ex) { log.accept("No se pudo leer "+p.getFileName()+": "+ex.getMessage()); }
            }
        }
        out.sort(Comparator.comparing((Local s)->s.snapshot.get("createdAt")).reversed()); return out;
    }
    public synchronized Local backup(Config.World world,String reason) throws IOException {
        guard.requireClosed(); FilesEx.separate(config.saves(),config.home);
        Map<String,Object> m=new LinkedHashMap<>(); m.put("worldId",world.id()); m.put("mode",world.mode); m.put("name",world.name);
        m.put("deviceId",config.get("deviceId")); m.put("deviceName",config.get("deviceName")); m.put("gameBuild",config.get("gameBuild"));
        m.put("destination",world.get("destination")); m.put("parentId",world.get("head")); m.put("reason",reason);
        ArchiveEngine.Snapshot snap=engine.create(world.path(),config.archives(),m);
        String status=world.get("destination").equals("local")?"local":"pending";
        register(snap,status,""); world.set("head",snap.id()); world.set("fingerprint",snap.get("fingerprint"));
        prune(world); log.accept("Respaldo verificado: "+world.name+" · "+snap.id()); return new Local(snap,FilesEx.readJson(metadata(snap.id())));
    }
    public synchronized void queue(Local local) throws IOException {
        Map<String,Object> state=FilesEx.readJson(metadata(local.snapshot.id()));
        if(Json.str(state,"status").equals("synced")) return;
        state.put("status","pending"); FilesEx.json(metadata(local.snapshot.id()),state);
    }
    public synchronized void sync() throws IOException {
        List<Local> pending=history(null).stream().filter(l->l.status().equals("pending")).sorted(Comparator.comparing(l->l.snapshot.get("createdAt"))).toList();
        for(Local local:pending) {
            Map<String,Object> state=new LinkedHashMap<>(local.state);
            try {
                ArchiveEngine.Snapshot verified=engine.verify(local.snapshot.archive(),local.snapshot.sha256());
                CloudProvider.Remote remote=cloud.upload(verified);
                state.put("status","synced"); state.put("remoteId",remote.fileId()); state.put("lastError",""); FilesEx.json(metadata(verified.id()),state);
                prune(config.world(verified.get("mode"),verified.get("name")));
            } catch(IOException ex) {
                state.put("lastError",ex.getMessage()); FilesEx.json(metadata(local.snapshot.id()),state); throw ex;
            }
        }
    }
    public synchronized Local download(CloudProvider.Remote remote) throws IOException {
        Path zip=cloud.download(remote,config.archives()); ArchiveEngine.Snapshot snap=engine.verify(zip,remote.get("sha256"));
        if(!snap.id().equals(remote.id())||!snap.get("worldId").equals(remote.get("world"))) throw new IOException("El manifiesto no coincide con la versión de la nube.");
        register(snap,"synced",remote.fileId()); log.accept("Copia descargada y verificada: "+snap.get("name")); return new Local(snap,FilesEx.readJson(metadata(snap.id())));
    }
    public synchronized ArchiveEngine.RestoreResult restore(Local local,String mode,String name,boolean replace) throws IOException {
        guard.requireClosed(); FilesEx.separate(config.saves(),config.home); FilesEx.safeName(mode); FilesEx.safeName(name);
        if(mode.equalsIgnoreCase("Multiplayer")) throw new IOException("Esta versión solo restaura partidas de un jugador.");
        ArchiveEngine.Snapshot snap=engine.verify(local.snapshot.archive(),local.snapshot.sha256()); Config.World target=config.world(mode,name);
        restorePins.add(snap.id());
        try {
        if(Files.exists(target.path(),LinkOption.NOFOLLOW_LINKS)) {
            if(!replace) throw new IOException("Ya existe una partida con ese nombre. Elegí otro nombre o confirmá reemplazarla.");
            backup(target,"pre_restore");
        }
        ArchiveEngine.RestoreResult result=engine.restore(snap.archive(),config.saves(),mode,name,snap.sha256());
        // Configuration follows the successful installation, never a failed restore.
        target.set("id",snap.get("worldId")); target.set("head",snap.id()); target.set("fingerprint",snap.get("fingerprint"));
        log.accept("Partida restaurada: "+mode+"/"+name+(result.previous()==null?"":". La carpeta anterior se conserva en "+result.previous())); return result;
        } finally { restorePins.remove(snap.id()); }
    }
    public synchronized Local importArchive(Path archive) throws IOException {
        ArchiveEngine.Snapshot verified=engine.verify(archive,null); Path target=config.archives().resolve(verified.id()+".zip");
        if(Files.exists(target)) {
            if(!FilesEx.hash(target,"SHA-256").equals(verified.sha256())) throw new IOException("ID duplicado con contenido distinto.");
        } else {
            Path temp=Files.createTempFile(config.archives(),".import-",".part");
            try { Files.copy(archive,temp,StandardCopyOption.REPLACE_EXISTING); engine.verify(temp,verified.sha256()); FilesEx.move(temp,target,false); }
            finally { Files.deleteIfExists(temp); }
        }
        ArchiveEngine.Snapshot saved=new ArchiveEngine.Snapshot(target,verified.manifest(),verified.sha256(),verified.bytes());
        if(!Files.exists(metadata(saved.id()))) register(saved,"local",""); return new Local(saved,FilesEx.readJson(metadata(saved.id())));
    }
    public synchronized void deleteLocal(Local local) throws IOException {
        if(local.status().equals("pending")) throw new IOException("La copia tiene una subida pendiente. Subila antes de eliminarla.");
        List<Local> same=history(null).stream().filter(s->s.snapshot.get("worldId").equals(local.snapshot.get("worldId"))).toList();
        if(same.size()<=1) throw new IOException("Se conserva al menos un respaldo local por partida.");
        Files.delete(local.snapshot.archive()); Files.deleteIfExists(metadata(local.snapshot.id()));
    }
    private void prune(Config.World world) throws IOException {
        int keep=world.get("destination").equals("drive")?1:world.retention(); int kept=0;
        for(Local local:history(world)) {
            if(restorePins.contains(local.snapshot.id())||local.status().equals("pending")||local.snapshot.get("reason").equals("pre_restore")) continue;
            if(++kept>keep) { Files.delete(local.snapshot.archive()); Files.deleteIfExists(metadata(local.snapshot.id())); }
        }
    }
    public synchronized void deleteCloud(CloudProvider.Remote remote) throws IOException {
        cloud.delete(remote);
        Path meta=metadata(remote.id());
        if(Files.exists(meta)) { Map<String,Object> state=FilesEx.readJson(meta); state.put("status","local"); state.put("remoteId",""); FilesEx.json(meta,state); }
    }
    /** Immutable ancestry: multiple leaves mean PCs continued from different restore points. */
    public static Set<String> conflictedWorlds(List<CloudProvider.Remote> versions) {
        Map<String,Set<String>> leaves=new HashMap<>(); Set<String> parents=new HashSet<>();
        for(var r:versions) { leaves.computeIfAbsent(r.get("world"),k->new HashSet<>()).add(r.id()); if(!r.get("parent").isBlank()) parents.add(r.get("parent")); }
        Set<String> conflict=new HashSet<>(); leaves.forEach((world,ids)-> { ids.removeAll(parents); if(ids.size()>1) conflict.add(world); }); return conflict;
    }
}
