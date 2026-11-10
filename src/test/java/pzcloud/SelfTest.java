package pzcloud;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.zip.*;

/** Offline integration suite: disposable worlds and a loopback Google API simulator only. */
public final class SelfTest {
    private static int passed;
    private static Path root;
    @FunctionalInterface interface Check { void run() throws Exception; }
    private static void test(String name,Check check) throws Exception { check.run(); passed++; System.out.println("PASS "+name); }
    static void eq(Object a,Object b) { if(!Objects.equals(a,b)) throw new AssertionError("Expected "+b+", got "+a); }
    static void ok(boolean value) { if(!value) throw new AssertionError("Condition failed"); }
    static void fails(Check action) throws Exception { try { action.run(); } catch(IOException expected) { return; } throw new AssertionError("Expected IOException"); }
    static final class Guard extends GameGuard { boolean active; public boolean running() { return active; } }
    static Path dir(String name) throws IOException { Path p=root.resolve(name); Files.createDirectories(p); return p; }
    static Map<String,Object> metadata(String name) { return new LinkedHashMap<>(Map.of("worldId",UUID.randomUUID().toString(),"name",name,"mode","Sandbox","gameBuild","42.22.1","deviceId","pc-test","deviceName","PC test","reason","manual","parentId","")); }
    static Path world(String name) throws IOException {
        Path p=dir(name); Files.createDirectories(p.resolve("nested/empty")); Files.writeString(p.resolve("map_ver.bin"),"42.22.1");
        Files.write(p.resolve("players.db"),new byte[]{0,1,2,3,-1,13,10}); Files.writeString(p.resolve("nested/área.txt"),"Santiago · partida de prueba 🧟"); Files.writeString(p.resolve("mods.txt"),"M16Expanded\nPZCloudManagement"); return p;
    }
    static SaveManager manager(String name,Guard guard,CloudProvider cloud) throws IOException {
        Config c=new Config(dir(name+"/app")); c.set("zomboid",dir(name+"/Zomboid").toString()); return new SaveManager(c,guard,cloud,s->{});
    }
    public static void main(String[] args) throws Exception {
        if(args.length>0&&args[0].equals("process-probe")) { System.out.println("READY"); System.out.flush(); System.in.read(); return; }
        root=Files.createTempDirectory("pzcloud-tests-").toRealPath();
        try {
            test("Real process enumeration recognizes a running PZ-marked Java process",()-> {
                String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
                Process probe=new ProcessBuilder(java,"-Dtest.pz.marker=zombie.GameWindow","-cp",System.getProperty("java.class.path"),SelfTest.class.getName(),"process-probe").start();
                try { eq(new BufferedReader(new InputStreamReader(probe.getInputStream())).readLine(),"READY"); ok(new GameGuard().running()); } finally { probe.getOutputStream().close(); if(!probe.waitFor(5,TimeUnit.SECONDS)) probe.destroyForcibly(); }
            });
            jsonTests(); instanceTests(); archiveTests(); managerTests(); v2Tests(); cloudTests(); authTests(); signInTests();
            System.out.println("OK: "+passed+" integration tests; no real game saves or Google account accessed.");
        } finally { FilesEx.deleteTree(root); }
    }
    static void jsonTests() throws Exception {
        test("JSON Unicode/escapes and large integer round trip",()-> { Map<String,Object> map=Map.of("name","á\n\\\"🧟","size",9007199254740993L,"empty",List.of()); eq(Json.object(Json.parse(Json.encode(map))),map); });
        test("Reject duplicate JSON keys and malformed values",()-> { for(String s:List.of("{\"x\":1,\"x\":2}","[1,]","01","true false","\"\\uZZZZ\"","{\"x\":}")) fails(()->Json.parse(s)); });
        test("Portable safe paths reject traversal, ADS, device names",()-> { for(String s:List.of("../x","/etc/file","a//b","a\\b","file:stream","CON.txt","trailing.","trailing ","a/../b")) fails(()->FilesEx.safeRelative(s)); FilesEx.safeRelative("nested/área.txt"); });
    }
    static void instanceTests() throws Exception {
        test("Identical installed builds may reuse a running instance",()-> {
            Path home=dir("instances/same"); try(AppInstance first=AppInstance.acquire(home,"build-one")) { ok(first.owned()); try(AppInstance second=AppInstance.acquire(home,"build-one")) { ok(!second.owned()); ok(second.matching()); } ok(Files.exists(home.resolve("manager.instance.json"))); }
            try(AppInstance next=AppInstance.acquire(home,"build-one")) { ok(next.owned()); } ok(!Files.exists(home.resolve("manager.instance.json")));
        });
        test("A different Google bundle cannot silently reuse another build",()-> {
            Path home=dir("instances/different"); try(AppInstance first=AppInstance.acquire(home,"local-only-build")) { try(AppInstance next=AppInstance.acquire(home,"google-included-build")) { ok(!next.owned()); ok(!next.matching()); } eq(Json.str(FilesEx.readJson(home.resolve("manager.instance.json")),"fingerprint"),"local-only-build"); }
        });
        test("An older process without installation metadata requires closing explicitly",()-> {
            Path home=dir("instances/legacy"); try(var channel=java.nio.channels.FileChannel.open(home.resolve("manager.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE); var lock=channel.lock(); AppInstance incoming=AppInstance.acquire(home,"new-build")) { ok(!incoming.owned()); ok(!incoming.matching()); ok(lock.isValid()); }
        });
        test("Installation diagnostics work without a user session and contain no OAuth secrets",()-> {
            Path output=dir("instances/report").resolve("installation.json"); Installation.writeDiagnostic(output); var report=FilesEx.readJson(output); eq(Json.str(report,"version"),Installation.VERSION); eq(Json.str(report,"personalSessionCreated"),"false"); String raw=Files.readString(output); ok(!raw.contains("client_secret")); ok(!raw.contains("refresh_token")); ok(!raw.contains("access_token")); ok(report.containsKey("application")); ok(report.containsKey("build"));
        });
    }
    static void archiveTests() throws Exception {
        Guard guard=new Guard(); ArchiveEngine engine=new ArchiveEngine(guard,s->{}); Path source=world("archive/source");
        var snap=engine.create(source,dir("archive/backups"),metadata("Mundo ñ"));
        test("Snapshot SHA-256 verifies binary files, Unicode, empty directories",()-> { var verified=engine.verify(snap.archive(),snap.sha256()); eq(verified.id(),snap.id()); ok(Files.exists(source.resolve("nested/empty"))); eq(FilesEx.hash(source.resolve("players.db"),"SHA-256"),HexFormat.of().formatHex(FilesEx.digest("SHA-256").digest(new byte[]{0,1,2,3,-1,13,10}))); });
        test("Restore to another PC preserves complete byte content",()-> {
            Path saves=dir("archive/other/Saves"); var restored=engine.restore(snap.archive(),saves,"Sandbox","Mundo ñ",snap.sha256()); eq(engine.fingerprint(restored.target()),snap.get("fingerprint")); ok(Files.isDirectory(restored.target().resolve("nested/empty")));
        });
        test("Overwrite restore retains previous world directory",()-> {
            Path saves=dir("archive/overwrite/Saves"), target=saves.resolve("Sandbox/Existing"); Files.createDirectories(target); Files.writeString(target.resolve("progress.txt"),"new progress");
            var result=engine.restore(snap.archive(),saves,"Sandbox","Existing",snap.sha256()); eq(Files.readString(result.previous().resolve("progress.txt")),"new progress"); ok(Files.exists(target.resolve("players.db"))); eq(Json.str(FilesEx.readJson(result.journal()),"phase"),"complete");
        });
        test("Wrong archive SHA refuses restore and preserves current progress",()-> {
            Path saves=dir("archive/wrong/Saves"), target=saves.resolve("Sandbox/Current"); Files.createDirectories(target); Files.writeString(target.resolve("progress"),"keep");
            fails(()->engine.restore(snap.archive(),saves,"Sandbox","Current","0".repeat(64))); eq(Files.readString(target.resolve("progress")),"keep");
        });
        test("Corrupted payload is detected even when ZIP CRC is recomputed",()-> {
            Path corrupt=dir("archive/corrupt").resolve("bad.zip"); rewrite(snap.archive(),corrupt,"save/players.db",new byte[]{9,9,9,9,9,9,9},null); fails(()->engine.verify(corrupt,null));
        });
        test("ZIP traversal manifest cannot escape staging",()-> {
            Path bad=dir("archive/traversal").resolve("bad.zip"); Map<String,Object> m=Json.object(Json.parse(Json.encode(snap.manifest()))); Json.object(Json.array(m.get("files")).get(0)).put("path","../../escape"); rewrite(snap.archive(),bad,null,null,m); fails(()->engine.restore(bad,dir("archive/traversal/Saves"),"Sandbox","Safe",null)); ok(!Files.exists(root.resolve("escape")));
        });
        test("Unlisted archive entries are rejected",()-> {
            Path bad=dir("archive/extra").resolve("bad.zip"); rewrite(snap.archive(),bad,"extra",new byte[]{1},null); fails(()->engine.verify(bad,null));
        });
        test("Case-colliding archive paths are rejected",()-> {
            Map<String,Object> m=Json.object(Json.parse(Json.encode(snap.manifest()))); List<Object> files=Json.array(m.get("files")); Map<String,Object> first=Json.object(files.get(0)), second=Json.object(files.get(1)); first.put("path","a.txt"); second.put("path","A.txt"); Path bad=dir("archive/case").resolve("bad.zip"); rewrite(snap.archive(),bad,null,null,m); fails(()->engine.verify(bad,null));
        });
        test("Implausible expanded archive size is rejected before extraction",()-> {
            Map<String,Object> m=Json.object(Json.parse(Json.encode(snap.manifest()))); Json.object(Json.array(m.get("files")).get(0)).put("size",ArchiveEngine.MAX_BYTES+1); Path bad=dir("archive/bomb").resolve("bad.zip"); rewrite(snap.archive(),bad,null,null,m); fails(()->engine.verify(bad,null));
        });
        test("Game-open guard blocks both backup and restore",()-> { guard.active=true; try { fails(()->engine.create(source,dir("archive/blocked"),metadata("Blocked"))); fails(()->engine.restore(snap.archive(),dir("archive/blocked/Saves"),"Sandbox","Blocked",snap.sha256())); } finally { guard.active=false; } });
        test("Backup root overlapping world is rejected",()-> { fails(()->engine.create(source,source.resolve("backups"),metadata("Bad"))); fails(()->engine.create(source,source.getParent(),metadata("Bad"))); });
        test("Mutation during compression publishes no backup",()-> {
            Path moving=world("archive/moving"), output=dir("archive/moving-backups"); AtomicBoolean changed=new AtomicBoolean();
            ArchiveEngine racing=new ArchiveEngine(guard,message->{ if(message.startsWith("Comprimiendo")&&changed.compareAndSet(false,true)) try { Files.writeString(moving.resolve("mods.txt"),"changed during snapshot"); } catch(IOException ex) { throw new UncheckedIOException(ex); } });
            fails(()->racing.create(moving,output,metadata("Moving"))); try(var files=Files.list(output)) { eq(files.count(),0L); }
        });
        test("Game starting during snapshot prevents publication",()-> {
            Path output=dir("archive/starting"); ArchiveEngine racing=new ArchiveEngine(guard,message->{ if(message.startsWith("Comprimiendo")) guard.active=true; });
            try { fails(()->racing.create(source,output,metadata("Starting"))); try(var files=Files.list(output)) { eq(files.count(),0L); } } finally { guard.active=false; }
        });
        test("Symlink files/root are rejected when OS supports links",()-> {
            Path linked=world("archive/linkworld"), link=linked.resolve("linked.txt");
            try { Files.createSymbolicLink(link,source.resolve("mods.txt")); } catch(IOException|UnsupportedOperationException ex) { System.out.println("  SKIP symlink fixture: OS permission"); return; }
            fails(()->engine.create(linked,dir("archive/links-out"),metadata("Links")));
            Path rootLink=root.resolve("archive/root-link"); Files.createSymbolicLink(rootLink,source); fails(()->engine.create(rootLink,dir("archive/root-links-out"),metadata("Link root")));
        });
        test("Crash journal recovers old world without overwriting an installed one",()-> {
            Path saves=dir("archive/recovery/Saves"),mode=Files.createDirectories(saves.resolve("Sandbox")); String tx=UUID.randomUUID().toString(), old=".pzcloud-previous-"+tx;
            Files.createDirectories(mode.resolve(old)); Files.writeString(mode.resolve(old+"/current"),"recover me"); Path journal=mode.resolve(".pzcloud-restore-"+tx+".json");
            FilesEx.json(journal,Map.of("phase","previous_moved","target","World","previous",old)); engine.recover(saves); eq(Files.readString(mode.resolve("World/current")),"recover me");
            Files.createDirectories(mode.resolve(old)); Files.writeString(mode.resolve(old+"/current"),"old"); Files.writeString(mode.resolve("World/current"),"installed"); engine.recover(saves); eq(Files.readString(mode.resolve("World/current")),"installed");
        });
        test("Completed journals never resurrect intentionally removed worlds",()-> {
            Path saves=dir("archive/completed/Saves"),mode=Files.createDirectories(saves.resolve("Sandbox")); String tx=UUID.randomUUID().toString(), old=".pzcloud-previous-"+tx;
            Files.createDirectories(mode.resolve(old)); FilesEx.json(mode.resolve(".pzcloud-restore-"+tx+".json"),Map.of("phase","complete","target","Deleted","previous",old)); engine.recover(saves); ok(!Files.exists(mode.resolve("Deleted")));
        });
    }
    static void rewrite(Path source,Path target,String replace,byte[] bytes,Map<String,Object> manifest) throws IOException {
        boolean found=false;
        try(ZipFile zip=new ZipFile(source.toFile()); ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(target))) {
            for(var entries=zip.entries();entries.hasMoreElements();) {
                ZipEntry entry=entries.nextElement(); out.putNextEntry(new ZipEntry(entry.getName()));
                if(entry.getName().equals(ArchiveEngine.MANIFEST)&&manifest!=null) out.write(Json.encode(manifest).getBytes(StandardCharsets.UTF_8));
                else if(entry.getName().equals(replace)) { out.write(bytes); found=true; }
                else try(InputStream in=zip.getInputStream(entry)) { in.transferTo(out); }
                out.closeEntry();
            }
            if(replace!=null&&!found) { out.putNextEntry(new ZipEntry(replace)); out.write(bytes); out.closeEntry(); }
        }
    }
    static final class FakeCloud implements CloudProvider {
        boolean offline; int uploads; final Map<String,ArchiveEngine.Snapshot> saved=new LinkedHashMap<>();
        final Path storage; FakeCloud() throws IOException { storage=dir("fakecloud/"+UUID.randomUUID()); }
        public List<Remote> list() { return saved.values().stream().map(FakeCloud::remote).toList(); }
        static Remote remote(ArchiveEngine.Snapshot s) { return new Remote("file_"+s.id(),s.id()+".zip",s.bytes(),"",Map.of("pzcloud","1","snapshot",s.id(),"sha256",s.sha256(),"world",s.get("worldId"),"created",s.get("createdAt"),"parent",s.get("parentId"),"saveName",s.get("name"),"mode",s.get("mode"),"device","PC")); }
        public Remote upload(ArchiveEngine.Snapshot s) throws IOException { if(offline) throw new IOException("offline"); uploads++; Path copy=storage.resolve(s.id()+".zip"); Files.copy(s.archive(),copy,StandardCopyOption.REPLACE_EXISTING); saved.put(s.id(),new ArchiveEngine.Snapshot(copy,s.manifest(),s.sha256(),s.bytes())); return remote(s); }
        public Path download(Remote r,Path dir) throws IOException { Path result=dir.resolve(r.id()+".zip"); if(!Files.exists(result)) Files.copy(saved.get(r.id()).archive(),result); return result; }
        public void delete(Remote r) { saved.remove(r.id()); }
    }
    static void managerTests() throws Exception {
        Guard guard=new Guard(); FakeCloud cloud=new FakeCloud(); SaveManager manager=manager("manager",guard,cloud);
        Path path=manager.config.saves().resolve("Sandbox/My World"); Files.createDirectories(path); Files.writeString(path.resolve("players.db"),"first"); Config.World w=manager.config.world("Sandbox","My World");
        test("Discover worlds with stable identities and skip Multiplayer/internal folders",()-> {
            Files.createDirectories(manager.config.saves().resolve("Multiplayer/Host")); Files.createDirectories(manager.config.saves().resolve("Sandbox/.pzcloud-old")); Files.createDirectories(manager.config.saves().resolve("Apocalypse/My World"));
            eq(manager.worlds().size(),2); ok(!manager.config.world("Apocalypse","My World").id().equals(w.id())); eq(new Config(manager.config.home).world("Sandbox","My World").id(),w.id());
        });
        var first=manager.backup(w,"manual");
        test("Offline upload remains pending across application restart",()-> {
            cloud.offline=true; fails(manager::sync); eq(manager.history(w).get(0).status(),"pending");
            SaveManager restarted=new SaveManager(new Config(manager.config.home),guard,cloud,s->{}); eq(restarted.history(w).get(0).status(),"pending");
            cloud.offline=false; restarted.sync(); eq(restarted.history(w).get(0).status(),"synced"); int count=cloud.uploads; restarted.sync(); eq(cloud.uploads,count);
        });
        test("Missing sidecar recovers durable cloud queue from archive manifest",()-> {
            var queued=manager.backup(w,"manual"); Files.delete(manager.config.archives().resolve(queued.snapshot().id()+".meta.json")); eq(manager.history(w).get(0).status(),"pending"); manager.sync();
        });
        test("Retention preserves every pending upload",()-> {
            w.set("retention",1); manager.backup(w,"manual"); manager.backup(w,"manual"); ok(manager.history(w).stream().filter(l->l.status().equals("pending")).count()>=2); manager.sync(); eq(manager.history(w).size(),1);
        });
        test("Restore creates pre-restore archive and keeps latest progress",()-> {
            var remote=cloud.list().stream().filter(r->r.id().equals(first.snapshot().id())).findFirst().orElseThrow(); var old=manager.download(remote); Files.writeString(path.resolve("players.db"),"latest progress");
            var restored=manager.restore(old,"Sandbox","My World",true); eq(Files.readString(path.resolve("players.db")),"first"); eq(Files.readString(restored.previous().resolve("players.db")),"latest progress"); ok(manager.history(w).stream().anyMatch(l->l.snapshot().get("reason").equals("pre_restore")));
        });
        test("CLI-style restore refuses overwrite without explicit replace",()-> { var local=manager.history(w).get(0); Files.writeString(path.resolve("players.db"),"keep existing"); fails(()->manager.restore(local,"Sandbox","My World",false)); eq(Files.readString(path.resolve("players.db")),"keep existing"); });
        test("Cloud snapshot can restore on second PC and adopt world identity",()-> {
            SaveManager other=manager("second-pc",guard,cloud); var remote=cloud.list().get(0); var local=other.download(remote); other.restore(local,"Sandbox","Other World",false); eq(other.config.world("Sandbox","Other World").id(),local.snapshot().get("worldId")); ok(Files.exists(other.config.saves().resolve("Sandbox/Other World/players.db")));
        });
        test("Immutable divergent histories are flagged without merging",()-> {
            var a=new CloudProvider.Remote("a","a",1,"",Map.of("world","w","snapshot","a","parent",""));
            var b=new CloudProvider.Remote("b","b",1,"",Map.of("world","w","snapshot","b","parent","a"));
            var c=new CloudProvider.Remote("c","c",1,"",Map.of("world","w","snapshot","c","parent","a"));
            ok(SaveManager.conflictedWorlds(List.of(a,b)).isEmpty()); ok(SaveManager.conflictedWorlds(List.of(a,b,c)).contains("w"));
        });
        test("Automatic exit waits for full process shutdown and creates one version",()-> {
            w.set("automatic",true); w.set("trigger","after_exit"); w.set("destination","local"); AtomicLong clock=new AtomicLong(1000); SessionMonitor monitor=new SessionMonitor(manager,s->{},clock::get); int before=manager.history(w).size();
            guard.active=true; monitor.tick(); Files.writeString(path.resolve("players.db"),"session update"); eq(manager.history(w).size(),before); guard.active=false; monitor.tick(); clock.addAndGet(11000); monitor.tick();
            ok(manager.history(w).stream().anyMatch(l->l.snapshot().get("reason").equals("after_exit"))); var id=manager.history(w).get(0).snapshot().id(); monitor.tick(); eq(manager.history(w).get(0).snapshot().id(),id);
        });
        test("Pending exit survives companion restart",()-> {
            guard.active=true; new SessionMonitor(manager,s->{}).tick(); Files.writeString(path.resolve("players.db"),"restart-session"); guard.active=false; AtomicLong clock=new AtomicLong(1000); SessionMonitor restarted=new SessionMonitor(manager,s->{},clock::get); restarted.tick(); clock.addAndGet(11000); restarted.tick(); eq(manager.history(w).get(0).snapshot().get("reason"),"after_exit");
        });
        test("Before-launch backs up configured worlds and blocks game-open state",()-> {
            w.set("trigger","before_enter"); SessionMonitor monitor=new SessionMonitor(manager,s->{}); monitor.beforeLaunch(); eq(manager.history(w).get(0).snapshot().get("reason"),"before_enter"); guard.active=true; try { fails(monitor::beforeLaunch); } finally { guard.active=false; }
        });
        test("File bridge deduplicates requests and cannot restore live worlds",()-> {
            AtomicInteger opened=new AtomicInteger(); GameBridge bridge=new GameBridge(manager,opened::incrementAndGet,s->{}); Path request=manager.config.zomboid().resolve("Lua/pzcloud-request.txt"); FilesEx.atomicWrite(request,"1|request_1|OPEN"); bridge.tick(); bridge.tick(); eq(opened.get(),1);
            new GameBridge(manager,opened::incrementAndGet,s->{}).tick(); eq(opened.get(),1); FilesEx.atomicWrite(request,"1|request_2|LIST"); bridge.tick(); ok(Files.readString(request.getParent().resolve("pzcloud-catalog.txt")).contains("My World"));
            String before=Files.readString(path.resolve("players.db")); FilesEx.atomicWrite(request,"1|request_3|RESTORE|../../evil"); bridge.tick(); eq(Files.readString(path.resolve("players.db")),before); ok(Files.readString(request.getParent().resolve("pzcloud-status.txt")).contains("ERROR"));
        });
        test("Explicit cloud deletion updates local sync status",()-> { manager.queue(manager.history(w).get(0)); manager.sync(); var synced=manager.history(w).stream().filter(l->l.status().equals("synced")).findFirst(); ok(synced.isPresent()); if(synced.isPresent()) { var remote=cloud.list().stream().filter(r->r.id().equals(synced.get().snapshot().id())).findFirst().orElseThrow(); manager.deleteCloud(remote); ok(manager.history(w).stream().filter(l->l.snapshot().id().equals(remote.id())).allMatch(l->l.status().equals("local"))); } });
    }
    static void v2Tests() throws Exception {
        test("SHA-256 and MD5 streaming pass returns both correct checksums",()-> {
            Path p=dir("v2/hash").resolve("data.bin"); Files.writeString(p,"two hashes, one read"); var pair=FilesEx.hashes(p); eq(pair.sha256(),FilesEx.hash(p,"SHA-256")); eq(pair.md5(),FilesEx.hash(p,"MD5"));
        });
        Guard guard=new Guard(); FakeCloud fake=new FakeCloud(); SaveManager m=manager("v2/catalog",guard,fake);
        Path save=m.config.saves().resolve("Sandbox/World"); Files.createDirectories(save); Files.writeString(save.resolve("data.bin"),"progress"); var world=m.config.world("Sandbox","World"); world.set("destination","local"); var copy=m.backup(world,"manual");
        test("Old 1.x sidecar upgrades without changing identity, hash or delivery status",()-> {
            Path sidecar=m.config.archives().resolve(copy.snapshot().id()+".meta.json"); var state=FilesEx.readJson(sidecar); state.remove("summary"); state.remove("bytes"); state.remove("md5"); FilesEx.json(sidecar,state);
            var upgraded=m.history(world).get(0); eq(upgraded.snapshot().id(),copy.snapshot().id()); eq(upgraded.snapshot().sha256(),copy.snapshot().sha256()); eq(upgraded.status(),"local"); ok(Files.size(sidecar)<8192);
        });
        test("Catalog can list without opening ZIPs; restore still rejects corrupt payload",()-> {
            byte[] original=Files.readAllBytes(copy.snapshot().archive()); byte[] invalid=new byte[original.length]; Arrays.fill(invalid,(byte)7); Files.write(copy.snapshot().archive(),invalid);
            eq(m.history(world).size(),1); fails(()->m.restore(m.history(world).get(0),"Sandbox","World",true)); eq(Files.readString(save.resolve("data.bin")),"progress"); Files.write(copy.snapshot().archive(),original);
        });
        test("Local mode creates no HTTP selector/client threads",()-> {
            Set<Long> before=new HashSet<>(); Thread.getAllStackTraces().keySet().forEach(t->before.add(t.getId()));
            SaveManager local=manager("v2/no-network",new Guard(),null); local.history(null); ok(!local.canSync());
            ok(Thread.getAllStackTraces().keySet().stream().noneMatch(t->!before.contains(t.getId())&&t.getName().startsWith("HttpClient-")));
        });
        Path folder=dir("v2/cloud-folder"); FolderCloud cloud=new FolderCloud(folder,s->{}); var verified=m.engine.verify(copy.snapshot().archive(),copy.snapshot().sha256()); var remote=cloud.upload(verified);
        test("Folder copies report folder delivery rather than confirmed Google upload",()-> { eq(cloud.deliveredStatus(),"folder"); eq(remote.get("transport"),"folder"); eq(cloud.list().size(),1); });
        test("Folder backend round-trips verified snapshots to another PC",()-> { Path p=cloud.download(remote,dir("v2/download")); eq(m.engine.verify(p,verified.sha256()).id(),verified.id()); m.engine.restore(p,dir("v2/restore/Saves"),"Sandbox","Restored",verified.sha256()); eq(Files.readString(root.resolve("v2/restore/Saves/Sandbox/Restored/data.bin")),"progress"); });
        test("Folder retry is idempotent and cannot overwrite different content",()-> {
            cloud.upload(verified); eq(cloud.list().size(),1); Path p=folder.resolve("PZCloud-"+verified.id()+".zip"); byte[] old=Files.readAllBytes(p); Files.writeString(p,"different"); fails(()->cloud.upload(verified)); eq(Files.readString(p),"different"); Files.write(p,old);
        });
        test("Incomplete folder synchronization is hidden until ZIP arrives",()-> {
            Path p=folder.resolve("PZCloud-"+verified.id()+".zip"), parked=folder.resolve("arriving.part"); Files.move(p,parked); eq(cloud.list().size(),0); Files.move(parked,p); eq(cloud.list().size(),1);
        });
        test("Malformed and conflict-renamed folder metadata is ignored",()-> {
            Files.writeString(folder.resolve("PZCloud-broken.json"),"{broken"); Files.copy(folder.resolve("PZCloud-"+verified.id()+".json"),folder.resolve("PZCloud-conflicted.json")); eq(cloud.list().size(),1);
        });
        test("Damaged synced payload never becomes a published local copy",()-> {
            Path p=folder.resolve("PZCloud-"+verified.id()+".zip"); byte[] old=Files.readAllBytes(p),bad=old.clone(); bad[bad.length/2]^=1; Files.write(p,bad); Path dest=dir("v2/corrupt-download"); fails(()->cloud.download(remote,dest)); ok(!Files.exists(dest.resolve(verified.id()+".zip"))); Files.write(p,old);
        });
        test("Folder copy can pause uploads while manual download remains available",()-> {
            FolderCloud paused=new FolderCloud(folder,s->{},()->{ throw new IOException("playing"); }); fails(()->paused.upload(verified)); Path p=paused.download(remote,dir("v2/manual-download")); eq(FilesEx.hash(p,"SHA-256"),verified.sha256());
        });
        test("Folder backend rejects overlap with internal backup storage",()-> { FolderCloud overlap=new FolderCloud(m.config.archives(),s->{}); fails(()->overlap.upload(verified)); });
        test("Manual mode schedules no process polling, cloud polling or retry timer",()-> {
            AtomicInteger probes=new AtomicInteger(); GameGuard counting=new GameGuard() { public boolean running() { probes.incrementAndGet(); return false; } };
            SaveManager manual=new SaveManager(new Config(dir("v2/idle/app")),counting,fake,s->{}); manual.config.set("zomboid",dir("v2/idle/Zomboid").toString());
            ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1); CountDownLatch opened=new CountDownLatch(1);
            try(BackgroundService service=new BackgroundService(manual,worker,opened::countDown,()->{},s->{})) {
                service.start(); ok(!service.scheduledMonitoring()); ok(!service.scheduledRetry()); eq(probes.get(),0);
                FilesEx.atomicWrite(manual.config.home.resolve("open.request"),"open"); ok(opened.await(3,TimeUnit.SECONDS)); eq(probes.get(),0);
            } finally { worker.shutdownNow(); }
        });
        test("Automatic polling exists only while protection is enabled",()-> {
            SaveManager auto=manager("v2/automatic",new Guard(),fake); var w=auto.config.world("Sandbox","World"); w.set("automatic",true); ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1);
            try(BackgroundService service=new BackgroundService(auto,worker,()->{},()->{},s->{})) {
                service.start(); ok(service.scheduledMonitoring()); worker.submit(()->{}).get(); w.set("automatic",false); worker.submit(service::settingsChanged).get(); ok(!service.scheduledMonitoring());
            } finally { worker.shutdownNow(); }
        });
        test("Offline retries back off and stop when cloud is disabled",()-> {
            FakeCloud offline=new FakeCloud(); offline.offline=true; SaveManager pending=manager("v2/retry",new Guard(),offline); pending.config.update(Map.of("cloudMode","folder","syncFolder",dir("v2/retry-dest").toString()));
            Path p=pending.config.saves().resolve("Sandbox/World"); Files.createDirectories(p); Files.writeString(p.resolve("data"),"queued"); pending.backup(pending.config.world("Sandbox","World"),"manual"); CountDownLatch failed=new CountDownLatch(1); ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1);
            try(BackgroundService service=new BackgroundService(pending,worker,()->{},()->{},text->{ if(text.contains("offline")) failed.countDown(); })) {
                service.start(); ok(failed.await(4,TimeUnit.SECONDS)); worker.submit(()->{}).get(); ok(service.scheduledRetry()); eq(pending.pendingCount(),1);
                pending.config.set("cloudMode","none"); worker.submit(service::settingsChanged).get(); ok(!service.scheduledRetry());
            } finally { worker.shutdownNow(); }
        });
        test("Configuration batches preserve legacy world preferences",()-> {
            world.update(Map.of("automatic",true,"retention",7,"trigger","after_exit")); Config reloaded=new Config(m.config.home); var w=reloaded.world("Sandbox","World"); ok(w.automatic()); eq(w.retention(),7); eq(w.id(),world.id());
        });
    }
    // Real HTTP protocol tests follow; no requests leave the loopback interface.
    static void cloudTests() throws Exception {
        try(MockDrive mock=new MockDrive()) {
            Guard guard=new Guard(); ArchiveEngine engine=new ArchiveEngine(guard,s->{}); Path source=world("http/world"); byte[] big=new byte[6*1024*1024]; new Random(47).nextBytes(big); Files.write(source.resolve("chunks.bin"),big);
            var snap=engine.create(source,dir("http/backups"),metadata("Big World")); AtomicInteger refresh=new AtomicInteger();
            GoogleDrive drive=new GoogleDrive(force->{ if(force) refresh.incrementAndGet(); return "test-access"; },mock.base.resolve("drive/v3/"),mock.base.resolve("upload/drive/v3/"),dir("http/sessions"),s->{});
            test("Interrupted resumable upload persists session and resumes accepted bytes",()-> {
                mock.failUploadOnce=true; fails(()->drive.upload(snap)); ok(mock.uploadedBytes()>0); ok(mock.uploadedBytes()<snap.bytes()); var remote=drive.upload(snap); eq(remote.bytes(),snap.bytes()); eq(remote.get("sha256"),snap.sha256()); ok(mock.queriedStatus>0);
            });
            test("Upload completion is idempotent across retry/crash before local acknowledgement",()-> { int count=mock.files.size(); drive.upload(snap); eq(mock.files.size(),count); });
            var remote=drive.list().get(0);
            test("Ranged download resumes after a later chunk fails",()-> {
                Path dest=dir("http/download"); mock.failDownloadAt=4*1024*1024; fails(()->drive.download(remote,dest)); eq(Files.size(dest.resolve(snap.id()+".download")),4L*1024*1024); ok(!Files.exists(dest.resolve(snap.id()+".zip")));
                Path zip=drive.download(remote,dest); eq(FilesEx.hash(zip,"SHA-256"),snap.sha256()); engine.verify(zip,snap.sha256());
            });
            test("Full HTTP 200 download when Range is ignored is accepted only at exact size",()-> { mock.ignoreRange=true; Path zip=drive.download(remote,dir("http/full")); eq(FilesEx.hash(zip,"SHA-256"),snap.sha256()); mock.ignoreRange=false; });
            test("Incorrect Content-Range cannot publish a downloaded snapshot",()-> { mock.badRange=true; Path dest=dir("http/bad-range"); fails(()->drive.download(remote,dest)); ok(!Files.exists(dest.resolve(snap.id()+".zip"))); mock.badRange=false; });
            test("Downloaded SHA mismatch deletes bad partial and preserves local saves",()-> { mock.corruptDownload=true; Path dest=dir("http/bad-sha"); fails(()->drive.download(remote,dest)); ok(!Files.exists(dest.resolve(snap.id()+".zip"))); ok(!Files.exists(dest.resolve(snap.id()+".download"))); mock.corruptDownload=false; });
            test("401 retries with refreshed token",()-> { mock.unauthorizedOnce=true; drive.list(); ok(refresh.get()>0); });
            Path small=world("http/small"); var second=engine.create(small,dir("http/backups"),metadata("Small World")); drive.upload(second);
            test("Cloud list follows pagination and deletion removes only selected version",()-> { var all=drive.list(); eq(all.size(),2); drive.delete(all.stream().filter(r->r.id().equals(second.id())).findFirst().orElseThrow()); eq(drive.list().size(),1); });
            test("Untrusted resumable session host is refused before bearer transmission",()-> {
                var third=engine.create(small,dir("http/backups"),metadata("Third World")); mock.evilLocation=true; fails(()->drive.upload(third)); mock.evilLocation=false;
            });
            test("Reconnection deduplicates completed versions and resets another account's session IDs",()-> {
                AtomicReference<String> connection=new AtomicReference<>("connection-one");
                GoogleDrive reconnecting=new GoogleDrive(force->"test-access",mock.base.resolve("drive/v3/"),mock.base.resolve("upload/drive/v3/"),dir("http/reconnect-sessions"),message->{},connection::get);
                int count=mock.files.size(); var before=reconnecting.upload(snap); eq(mock.files.size(),count);
                connection.set("connection-two"); var same=reconnecting.upload(snap); eq(same.fileId(),before.fileId()); eq(mock.files.size(),count);
                mock.files.clear(); mock.data.clear(); connection.set("different-account"); var other=reconnecting.upload(snap); ok(!other.fileId().equals(before.fileId()));
            });
            test("Expired 4xx resumable session is cleared and can restart",()-> {
                var next=engine.create(small,dir("http/backups"),metadata("Expired Session")); mock.failUploadOnce=true; fails(()->drive.upload(next)); mock.expireStatusOnce=true; fails(()->drive.upload(next));
                var resumed=drive.upload(next); eq(resumed.get("sha256"),next.sha256());
            });
        }
    }
    static void authTests() throws Exception {
        test("Credential vault round-trips large payload without pipe deadlock",()-> {
            TokenVault vault=new TokenVault(dir("vault").resolve("session.token")); var value=Map.<String,Object>of("refresh_token","x".repeat(12000)); vault.write(value); eq(vault.read(),value); vault.delete(); ok(!vault.exists());
        });
        try(MockDrive mock=new MockDrive()) {
            Path home=dir("oauth/app"); GoogleAuth auth=new GoogleAuth(home,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("test"));
            test("OAuth uses PKCE S256, random state and loopback callback; invalid state is refused",()-> {
                auth.connect(uri->{
                    try {
                        Map<String,String> q=GoogleAuth.query(uri.getRawQuery()); eq(q.get("code_challenge_method"),"S256"); eq(q.get("scope"),GoogleAuth.SCOPE); ok(q.get("state").length()>=43); mock.challenge=q.get("code_challenge");
                        URI callback=URI.create(q.get("redirect_uri")); eq(callback.getHost(),"127.0.0.1"); HttpClient http=HttpClient.newHttpClient();
                        eq(http.send(HttpRequest.newBuilder(URI.create(callback+"?state=invalid&code=test")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode(),400);
                        eq(http.send(HttpRequest.newBuilder(URI.create(callback+"?state="+q.get("state")+"&code=test-code")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode(),200);
                    } catch(Exception ex) { throw new RuntimeException(ex); }
                }); ok(auth.connected()); eq(auth.accessToken(false),"access-initial"); ok(!Files.exists(home.resolve("google-client.json")));
            });
            test("Refresh token survives restart; refresh rotation retains long-lived token",()-> { GoogleAuth restarted=new GoogleAuth(home,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("test")); eq(restarted.accessToken(true),"access-refreshed"); ok(restarted.connected()); eq(new GoogleAuth(home,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("test")).accessToken(false),"access-refreshed"); });
            test("Invalid grant leaves backups untouched and reports reconnect",()-> { mock.invalidGrant=true; fails(()->auth.accessToken(true)); mock.invalidGrant=false; });
            test("Local client JSON cannot replace the bundled identity or an existing matching session",()-> { FilesEx.json(home.resolve("google-client.json"),desktopClient("other")); GoogleAuth unchanged=new GoogleAuth(home,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("test")); ok(unchanged.connected()); eq(unchanged.accessToken(true),"access-refreshed"); });
            test("Disconnect removes local credential; cancelled login stores no session",()-> {
                auth.disconnect(); ok(!Files.exists(home.resolve("google-session.token"))); fails(()->auth.connect(uri->{ try { var q=GoogleAuth.query(uri.getRawQuery()); HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(q.get("redirect_uri")+"?state="+q.get("state")+"&error=access_denied")).GET().build(),HttpResponse.BodyHandlers.ofString()); } catch(Exception ex) { throw new RuntimeException(ex); } })); ok(!auth.connected());
            });
            test("Drive readiness checks actual appData permission without uploading a test file",()-> {
                auth.connect(uri->approveLogin(uri,mock)); int before=mock.files.size(); auth.verifyDriveAccess(mock.base.resolve("drive-check?spaces=appDataFolder&pageSize=1&fields=files(id)")); eq(mock.files.size(),before); ok(mock.driveChecks>0);
            });
            test("Drive readiness reports disabled API without claiming a usable connection",()-> {
                mock.driveCheckStatus=403; try { auth.verifyDriveAccess(mock.base.resolve("drive-check")); throw new AssertionError("Disabled Drive must fail"); } catch(IOException ex) { ok(ex.getMessage().contains("Contactá al soporte")); } finally { mock.driveCheckStatus=200; } ok(auth.connected());
            });
            test("Drive readiness refreshes an expired access token once",()-> { mock.driveCheck401Once=true; int before=mock.driveChecks; auth.verifyDriveAccess(mock.base.resolve("drive-check")); eq(mock.driveChecks,before+2); eq(auth.accessToken(false),"access-refreshed"); });
            test("Drive readiness rejects an incomplete successful HTTP response",()-> { mock.driveBadCheck=true; try { fails(()->auth.verifyDriveAccess(mock.base.resolve("drive-check"))); } finally { mock.driveBadCheck=false; } });
            test("A local JSON with client and user tokens cannot enable an unconfigured runtime",()-> { Path clean=dir("oauth/clean"); var value=new LinkedHashMap<>(desktopClient("external")); value.put("refresh_token","DO_NOT_IMPORT"); FilesEx.json(clean.resolve("google-client.json"),value); GoogleAuth unavailable=new GoogleAuth(clean,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),Map::of); ok(!unavailable.configured()); ok(!unavailable.connected()); ok(!Files.exists(clean.resolve("google-session.token"))); });
        }
    }
    static Map<String,Object> desktopClient(String id) { return Map.of("installed",Map.of("client_id",id+".apps.googleusercontent.com","client_secret","public-desktop-test")); }
    static void approveLogin(java.net.URI uri,MockDrive mock) {
        try {
            var q=GoogleAuth.query(uri.getRawQuery()); mock.challenge=q.get("code_challenge");
            ok(q.get("prompt").contains("select_account"));
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(q.get("redirect_uri")+"?state="+q.get("state")+"&code=test-code")).GET().build(),HttpResponse.BodyHandlers.ofString()); eq(response.statusCode(),200);
        } catch(Exception ex) { throw new RuntimeException(ex); }
    }
    static void signInTests() throws Exception {
        test("Runtime ignores external JSON, environment and JVM client overrides",()-> {
            String java=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?"java.exe":"java").toString();
            ProcessBuilder check=new ProcessBuilder(java,"-Djava.awt.headless=true","-cp",System.getProperty("java.class.path"),"pzcloud.BundledClientSmoke"); check.environment().put("PZCLOUD_GOOGLE_CLIENT_ID","external.apps.googleusercontent.com"); check.environment().put("PZCLOUD_GOOGLE_CLIENT_SECRET","external-public-secret"); check.redirectErrorStream(true); Process process=check.start(); ok(process.waitFor(15,TimeUnit.SECONDS)); String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8); if(process.exitValue()!=0) throw new AssertionError(output);
        });
        try(MockDrive mock=new MockDrive()) {
            Path home=dir("signin/publisher-client"); GoogleAuth auth=new GoogleAuth(home,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("publisher"));
            test("Publisher-supplied client signs in without importing an end-user JSON",()-> { ok(auth.configured()); auth.connect(uri->approveLogin(uri,mock)); ok(auth.connected()); ok(!Files.exists(home.resolve("google-client.json"))); });
            test("A session for another application requires reconnecting without deleting its saved data",()-> { String before=FilesEx.hash(home.resolve("google-session.token"),"SHA-256"); GoogleAuth changed=new GoogleAuth(home,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("different-publisher")); ok(!changed.connected()); ok(!changed.sessionProblem().isBlank()); fails(()->changed.accessToken(true)); eq(FilesEx.hash(home.resolve("google-session.token"),"SHA-256"),before); });
            test("Unconfigured distribution never opens a fake Google login",()-> {
                GoogleAuth missing=new GoogleAuth(dir("signin/missing"),mock.base.resolve("token"),java.time.Duration.ofSeconds(1),Map::of); AtomicInteger opened=new AtomicInteger(); ok(!missing.configured()); ok(!missing.configurationProblem().isBlank()); fails(()->missing.connect(uri->opened.incrementAndGet())); eq(opened.get(),0); ok(!missing.isConnecting());
            });
            test("Cancelling browser wait stops callback and saves no account token",()-> {
                Path empty=dir("signin/cancel"); GoogleAuth waiting=new GoogleAuth(empty,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("publisher")); CountDownLatch shown=new CountDownLatch(1); AtomicReference<URI> page=new AtomicReference<>(); ExecutorService worker=Executors.newSingleThreadExecutor();
                try {
                    Future<Boolean> result=worker.submit(()-> { try { waiting.connect(uri->{ page.set(uri); shown.countDown(); }); return true; } catch(IOException ex) { return false; } });
                    ok(shown.await(2,TimeUnit.SECONDS)); ok(waiting.cancelConnection()); ok(!result.get(2,TimeUnit.SECONDS)); ok(!waiting.connected()); ok(!Files.exists(empty.resolve("google-session.token"))); ok(!waiting.isConnecting());
                    String callback=GoogleAuth.query(page.get().getRawQuery()).get("redirect_uri"); fails(()->HttpClient.newBuilder().connectTimeout(java.time.Duration.ofMillis(200)).build().send(HttpRequest.newBuilder(URI.create(callback)).timeout(java.time.Duration.ofMillis(300)).GET().build(),HttpResponse.BodyHandlers.ofString()));
                } finally { worker.shutdownNow(); }
            });
            test("Cancelling an account change retains the previous working session",()-> {
                CountDownLatch shown=new CountDownLatch(1); ExecutorService worker=Executors.newSingleThreadExecutor(); String previous=auth.connectionId();
                try { Future<Boolean> result=worker.submit(()-> { try { auth.connect(uri->shown.countDown()); return true; } catch(IOException ex) { return false; } }); ok(shown.await(2,TimeUnit.SECONDS)); ok(auth.cancelConnection()); ok(!result.get(2,TimeUnit.SECONDS)); ok(auth.connected()); eq(auth.connectionId(),previous); }
                finally { worker.shutdownNow(); }
            });
            test("Only one Google authorization attempt can run at a time",()-> {
                GoogleAuth pending=new GoogleAuth(dir("signin/concurrent"),mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("publisher")); CountDownLatch shown=new CountDownLatch(1); ExecutorService worker=Executors.newSingleThreadExecutor();
                try { Future<?> first=worker.submit(()-> { try { pending.connect(uri->shown.countDown()); } catch(IOException ignored) {} }); ok(shown.await(2,TimeUnit.SECONDS)); fails(()->pending.connect(uri->{})); ok(pending.isConnecting()); pending.cancelConnection(); first.get(2,TimeUnit.SECONDS); }
                finally { worker.shutdownNow(); }
            });
            test("Expired sign-in frees its local listener and can be retried",()-> {
                GoogleAuth expiring=new GoogleAuth(dir("signin/timeout"),mock.base.resolve("token"),java.time.Duration.ofMillis(80),()->desktopClient("publisher")); fails(()->expiring.connect(uri->{})); ok(!expiring.isConnecting()); expiring.connect(uri->approveLogin(uri,mock)); ok(expiring.connected());
            });
            test("Final session commit cannot be falsely reported as cancelled",()-> {
                GoogleAuth committing=new GoogleAuth(dir("signin/commit"),mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("publisher")); AtomicBoolean seen=new AtomicBoolean(); committing.connect(uri->approveLogin(uri,mock),phase->{ if(phase.equals("committing")) { seen.set(true); ok(!committing.cancelConnection()); } }); ok(seen.get()); ok(committing.connected());
            });
            test("An unreadable old sign-in token does not prevent local app startup",()-> {
                Path broken=dir("signin/broken"); Files.writeString(broken.resolve("google-session.token"),"not-a-session"); GoogleAuth recovered=new GoogleAuth(broken,mock.base.resolve("token"),java.time.Duration.ofSeconds(5),()->desktopClient("publisher")); ok(!recovered.connected()); ok(!recovered.sessionProblem().isBlank()); ok(recovered.configured()); ok(Files.exists(broken.resolve("google-session.token")));
            });
        }
        test("Publisher build embeds only application identity and reports configuration honestly",()-> {
            Path resources=dir("signin/build/resources"),report=root.resolve("signin/build/report.json"); Map<String,Object> doc=new LinkedHashMap<>(desktopClient("publisher")); doc.put("refresh_token","MUST_NOT_BE_PUBLISHED"); doc.put("access_token","PRIVATE_USER_TOKEN");
            ok(PublisherConfig.prepareDocument(resources,report,doc,true)); String text=Files.readString(resources.resolve("auth/google-client.json")); ok(!text.contains("MUST_NOT_BE_PUBLISHED")); ok(!text.contains("PRIVATE_USER_TOKEN")); ok(!Files.readString(report).contains("publisher.apps")); eq(Json.str(FilesEx.readJson(report),"googleClientBundled"),"true");
        });
        test("An unconfigured build clears stale Google identity and cannot claim ready",()-> {
            Path resources=dir("signin/build/resources"),report=root.resolve("signin/build/report.json"); ok(!PublisherConfig.prepareDocument(resources,report,Map.of(),false)); ok(!Files.exists(resources.resolve("auth/google-client.json"))); eq(Json.str(FilesEx.readJson(report),"googleClientBundled"),"false"); fails(()->PublisherConfig.prepareDocument(resources,report,Map.of(),true));
        });
        test("Publisher build rejects web clients and example placeholders",()-> {
            Path resources=dir("signin/invalid/resources"),report=root.resolve("signin/invalid/report.json"); fails(()->PublisherConfig.prepareDocument(resources,report,Map.of("web",Map.of("client_id","web.apps.googleusercontent.com")),true)); fails(()->PublisherConfig.prepareDocument(resources,report,Map.of("installed",Map.of("client_id","PLACEHOLDER")),true));
        });
    }
    static final class MockDrive implements AutoCloseable {
        final HttpServer server; final URI base; final Map<String,Map<String,Object>> files=new LinkedHashMap<>(), drafts=new LinkedHashMap<>();
        final Map<String,ByteArrayOutputStream> data=new HashMap<>(); int generated,queriedStatus;
        boolean expireStatusOnce,failUploadOnce,ignoreRange,badRange,corruptDownload,unauthorizedOnce,evilLocation,invalidGrant,driveCheck401Once,driveBadCheck; int driveCheckStatus=200,driveChecks; long failDownloadAt=-1; String challenge="";
        MockDrive() throws IOException { server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); base=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"); server.createContext("/",this::handle); server.start(); }
        long uploadedBytes() { return data.values().stream().mapToLong(ByteArrayOutputStream::size).sum(); }
        void send(HttpExchange ex,int code,Object value) throws IOException { byte[] b=Json.encode(value).getBytes(StandardCharsets.UTF_8); ex.getResponseHeaders().set("Content-Type","application/json"); ex.sendResponseHeaders(code,b.length); ex.getResponseBody().write(b); }
        void handle(HttpExchange ex) throws IOException {
            try {
                String path=ex.getRequestURI().getPath(),method=ex.getRequestMethod(); var query=GoogleAuth.query(ex.getRequestURI().getRawQuery());
                if(path.equals("/token")) {
                    var form=GoogleAuth.query(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                    if(invalidGrant) { send(ex,400,Map.of("error","invalid_grant")); return; }
                    if(form.get("grant_type").equals("authorization_code")) {
                        String actual=Base64.getUrlEncoder().withoutPadding().encodeToString(FilesEx.digest("SHA-256").digest(form.get("code_verifier").getBytes(StandardCharsets.US_ASCII))); eq(actual,challenge);
                        send(ex,200,Map.of("access_token","access-initial","refresh_token","refresh-test","expires_in",3600));
                    } else { eq(form.get("refresh_token"),"refresh-test"); send(ex,200,Map.of("access_token","access-refreshed","expires_in",3600)); } return;
                }
                if(path.equals("/drive-check")) {
                    driveChecks++; eq(method,"GET"); ok(Set.of("Bearer access-initial","Bearer access-refreshed").contains(ex.getRequestHeaders().getFirst("Authorization")));
                    if(driveCheck401Once) { driveCheck401Once=false; send(ex,401,Map.of("error","expired")); return; }
                    if(driveCheckStatus!=200) { send(ex,driveCheckStatus,Map.of("error",Map.of("errors",List.of(Map.of("reason","accessNotConfigured"))))); return; }
                    send(ex,200,driveBadCheck?Map.of("unexpected",true):Map.of("files",List.of())); return;
                }
                eq(ex.getRequestHeaders().getFirst("Authorization"),"Bearer test-access");
                if(unauthorizedOnce) { unauthorizedOnce=false; send(ex,401,Map.of("error","unauthorized")); return; }
                if(path.equals("/drive/v3/files/generateIds")) { eq(query.get("space"),"appDataFolder"); send(ex,200,Map.of("ids",List.of("file"+(++generated)))); return; }
                if(path.equals("/upload/drive/v3/files")) {
                    Map<String,Object> meta=Json.object(Json.parse(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8))); String id=Json.str(meta,"id"); drafts.put(id,meta); data.put(id,new ByteArrayOutputStream());
                    ex.getResponseHeaders().set("Location",evilLocation?"https://example.com/steal":base.resolve("session/"+id).toString()); ex.sendResponseHeaders(200,-1); return;
                }
                if(path.startsWith("/session/")) {
                    String id=path.substring("/session/".length()), range=ex.getRequestHeaders().getFirst("Content-Range");
                    if(range.startsWith("bytes */")) { queriedStatus++; if(expireStatusOnce) { expireStatusOnce=false; send(ex,400,Map.of("error","expiredSession")); return; } if(files.containsKey(id)) { send(ex,200,files.get(id)); return; } rangeResponse(ex,id); return; }
                    String[] rangeParts=range.substring(6).split("[-/]"); long start=Long.parseLong(rangeParts[0]),total=Long.parseLong(rangeParts[2]); eq(start,(long)data.get(id).size()); data.get(id).write(ex.getRequestBody().readAllBytes());
                    if(failUploadOnce) { failUploadOnce=false; send(ex,503,Map.of("error","interrupted")); return; }
                    if(data.get(id).size()==total) {
                        Map<String,Object> meta=new LinkedHashMap<>(drafts.get(id)); meta.put("size",Long.toString(total)); meta.put("md5Checksum",HexFormat.of().formatHex(FilesEx.digest("MD5").digest(data.get(id).toByteArray()))); files.put(id,meta); send(ex,200,meta);
                    } else rangeResponse(ex,id); return;
                }
                if(path.equals("/drive/v3/files")) {
                    eq(query.get("spaces"),"appDataFolder"); List<Map<String,Object>> all=new ArrayList<>(files.values()); int page=query.getOrDefault("pageToken","").isEmpty()?0:Integer.parseInt(query.get("pageToken"));
                    if(query.getOrDefault("q","").contains("key='snapshot'")) {
                        String value=query.get("q").split("value='",2)[1].split("'",2)[0];
                        all=all.stream().filter(f->{ try { return Json.str(Json.object(f.get("appProperties")),"snapshot").equals(value); } catch(IOException e) { throw new UncheckedIOException(e); } }).toList();
                    }
                    Map<String,Object> result=new LinkedHashMap<>(); result.put("files",page<all.size()?List.of(all.get(page)):List.of()); if(page+1<all.size()) result.put("nextPageToken",Integer.toString(page+1)); send(ex,200,result); return;
                }
                if(path.startsWith("/drive/v3/files/")) {
                    String id=path.substring("/drive/v3/files/".length());
                    if(method.equals("DELETE")) { files.remove(id); data.remove(id); ex.sendResponseHeaders(204,-1); return; }
                    if(!files.containsKey(id)) { send(ex,404,Map.of("error","notFound")); return; }
                    if(query.getOrDefault("alt","").equals("media")) {
                        byte[] bytes=data.get(id).toByteArray(); String[] range=ex.getRequestHeaders().getFirst("Range").substring(6).split("-"); int start=Integer.parseInt(range[0]),end=Integer.parseInt(range[1]);
                        if(start>=failDownloadAt&&failDownloadAt>=0) { failDownloadAt=-1; send(ex,503,Map.of("error","interrupted")); return; }
                        if(corruptDownload) bytes[start]^=1;
                        if(ignoreRange) { ex.sendResponseHeaders(200,bytes.length); ex.getResponseBody().write(bytes); }
                        else { ex.getResponseHeaders().set("Content-Range","bytes "+(badRange?start+1:start)+"-"+end+"/"+bytes.length); ex.sendResponseHeaders(206,end-start+1); ex.getResponseBody().write(bytes,start,end-start+1); } return;
                    }
                    send(ex,200,files.get(id)); return;
                }
                send(ex,404,Map.of("error","unknown"));
            } catch(Throwable exn) { exn.printStackTrace(); try { send(ex,500,Map.of("error",exn.toString())); } catch(IOException ignored) {} }
            finally { ex.close(); }
        }
        void rangeResponse(HttpExchange ex,String id) throws IOException { int size=data.get(id).size(); if(size>0) ex.getResponseHeaders().set("Range","bytes=0-"+(size-1)); ex.sendResponseHeaders(308,-1); }
        public void close() { server.stop(0); }
    }
}
