package pzcloud;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;

/** Only an identical installed JAR may reuse the already-running UI. */
final class AppInstance implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final Path marker;
    private final String owner;
    private final boolean matching;
    private boolean closed;
    private AppInstance(FileChannel channel,FileLock lock,Path marker,String owner,boolean matching) { this.channel=channel; this.lock=lock; this.marker=marker; this.owner=owner; this.matching=matching; }
    static AppInstance acquire(Path home,String fingerprint) throws IOException {
        Files.createDirectories(home); Path marker=home.resolve("manager.instance.json");
        FileChannel channel=FileChannel.open(home.resolve("manager.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock lock;
        try { lock=channel.tryLock(); } catch(OverlappingFileLockException ex) { lock=null; }
        if(lock==null) {
            channel.close(); boolean same=false;
            try { same=fingerprint.equals(Json.str(FilesEx.readJson(marker),"fingerprint")); } catch(IOException ignored) { /* Older editions have no marker. */ }
            return new AppInstance(null,null,marker,"",same);
        }
        String owner=UUID.randomUUID().toString();
        try { FilesEx.json(marker,Map.of("owner",owner,"fingerprint",fingerprint,"version",Installation.VERSION,"pid",ProcessHandle.current().pid())); }
        catch(IOException ex) { lock.release(); channel.close(); throw ex; }
        return new AppInstance(channel,lock,marker,owner,false);
    }
    boolean owned() { return lock!=null; }
    boolean matching() { return matching; }
    public synchronized void close() throws IOException {
        if(lock==null||closed) return; closed=true;
        try { if(Files.exists(marker)&&owner.equals(Json.str(FilesEx.readJson(marker),"owner"))) Files.deleteIfExists(marker); }
        finally { try { lock.release(); } finally { channel.close(); } }
    }
}
