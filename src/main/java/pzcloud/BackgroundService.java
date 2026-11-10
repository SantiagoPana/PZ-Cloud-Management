package pzcloud;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Sleeps on OS file events. Timers exist only for enabled protection or pending uploads. */
public final class BackgroundService implements AutoCloseable {
    private final SaveManager manager;
    private final ScheduledExecutorService worker;
    private final SessionMonitor monitor;
    private final GameBridge bridge;
    private final Runnable refresh;
    private final Consumer<String> log;
    private WatchService watcher;
    private ScheduledFuture<?> monitoring,retry;
    private long delay=30;
    private boolean closed;
    private String lastError="";
    public BackgroundService(SaveManager manager,ScheduledExecutorService worker,Runnable show,Runnable refresh,Consumer<String> log) throws IOException {
        this.manager=manager; this.worker=worker; this.refresh=refresh; this.log=log;
        monitor=new SessionMonitor(manager,log); bridge=new GameBridge(manager,show,log);
    }
    public void start() throws IOException {
        manager.history(null); // One-time migration/index recovery; summaries only after migration.
        watcher=FileSystems.getDefault().newWatchService();
        manager.config.home.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY);
        Path lua=manager.config.zomboid().resolve("Lua"); Files.createDirectories(lua);
        lua.register(watcher,StandardWatchEventKinds.ENTRY_CREATE,StandardWatchEventKinds.ENTRY_MODIFY);
        Thread thread=new Thread(this::watch,"pzcloud-file-events"); thread.setDaemon(true); thread.setPriority(Thread.MIN_PRIORITY); thread.start();
        worker.execute(()-> { try { bridge.tick(); } catch(Exception ex) { report(ex); } });
        settingsChanged();
    }
    private void watch() {
        try {
            while(!closed) {
                WatchKey key=watcher.take();
                for(WatchEvent<?> event:key.pollEvents()) {
                    String name=event.context()==null?"":event.context().toString();
                    if(name.equals("open.request")||name.equals("pzcloud-request.txt")) worker.execute(()-> {
                        if(closed) return;
                        try {
                            if(name.equals("open.request")) { if(Files.deleteIfExists(manager.config.home.resolve("open.request"))) bridge.showWindow(); }
                            else bridge.tick();
                        } catch(Exception ex) { report(ex); }
                    });
                }
                key.reset();
            }
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
        catch(ClosedWatchServiceException ignored) { /* Normal shutdown. */ }
        catch(RejectedExecutionException ignored) { /* Worker was stopped while closing. */ }
    }
    public GameBridge bridge() { return bridge; }
    public SessionMonitor monitor() { return monitor; }
    public void settingsChanged() {
        if(closed) return;
        if(manager.config.automaticExits()||monitor.pendingExit()) {
            if(monitoring==null||monitoring.isCancelled()) monitoring=worker.scheduleWithFixedDelay(this::poll,0,15,TimeUnit.SECONDS);
        } else if(monitoring!=null) { monitoring.cancel(false); monitoring=null; }
        changed();
    }
    public void changed() {
        if(closed) return;
        delay=30;
        if(retry!=null) retry.cancel(false);
        retry=null;
        if(manager.hasPending()&&manager.canSync()) retry=worker.schedule(this::sync,1,TimeUnit.SECONDS);
    }
    private void poll() {
        if(closed) return;
        try {
            long revision=monitor.revision(); monitor.tick();
            if(revision!=monitor.revision()) { refresh.run(); changed(); }
            if(!manager.config.automaticExits()&&!monitor.pendingExit()&&monitoring!=null) { monitoring.cancel(false); monitoring=null; }
        } catch(Exception ex) { report(ex); }
    }
    private void sync() {
        retry=null; if(closed||!manager.hasPending()||!manager.canSync()) return;
        try {
            manager.guard.requireClosed(); manager.sync(); delay=30; lastError=""; refresh.run();
        } catch(Exception ex) {
            report(ex);
            if(!closed&&manager.hasPending()&&manager.canSync()) { retry=worker.schedule(this::sync,delay,TimeUnit.SECONDS); delay=Math.min(600,delay*2); }
        }
    }
    private void report(Exception ex) {
        if(!lastError.equals(ex.getMessage())) { lastError=ex.getMessage(); log.accept("Pendiente: "+ex.getMessage()); }
    }
    public boolean scheduledMonitoring() { return monitoring!=null&&!monitoring.isCancelled(); }
    public boolean scheduledRetry() { return retry!=null&&!retry.isCancelled(); }
    public void close() throws IOException {
        closed=true; if(monitoring!=null) monitoring.cancel(false); if(retry!=null) retry.cancel(false); if(watcher!=null) watcher.close();
    }
}
