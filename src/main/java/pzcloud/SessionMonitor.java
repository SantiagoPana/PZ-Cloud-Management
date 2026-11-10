package pzcloud;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** Called from the single worker. Never copies files until the process has fully exited. */
public final class SessionMonitor {
    private final SaveManager manager;
    private final Consumer<String> log;
    private final Path journal;
    private boolean pending;
    private long closedSince,revision;
    public boolean pendingExit() { return pending; }
    public long revision() { return revision; }
    private final java.util.function.LongSupplier clock;
    public SessionMonitor(SaveManager manager,Consumer<String> log) throws IOException {
        this(manager,log,System::currentTimeMillis);
    }
    SessionMonitor(SaveManager manager,Consumer<String> log,java.util.function.LongSupplier clock) throws IOException {
        this.manager=manager; this.log=log; this.clock=clock; journal=manager.config.home.resolve("session-state.json");
        pending=Files.exists(journal)&&Json.str(FilesEx.readJson(journal),"pendingExit").equals("true");
    }
    public void tick() throws IOException {
        if(manager.guard.running()) {
            closedSince=0;
            if(!pending) { pending=true; FilesEx.json(journal,Map.of("pendingExit",true,"seenAt",Instant.now().toString())); log.accept("Zomboid abierto: respaldo automático preparado para el cierre."); }
            return;
        }
        if(!pending) return;
        if(closedSince==0) { closedSince=clock.getAsLong(); return; }
        if(clock.getAsLong()-closedSince<10_000) return;
        for(Config.World world:manager.worlds()) {
            if(!world.automatic()||world.get("trigger").equals("before_enter")) continue;
            String fingerprint=manager.engine.fingerprint(world.path());
            if(!fingerprint.equals(world.get("fingerprint"))) { manager.backup(world,"after_exit"); revision++; }
        }
        FilesEx.json(journal,Map.of("pendingExit",false)); pending=false;
    }
    public void beforeLaunch() throws IOException {
        manager.guard.requireClosed();
        for(Config.World world:manager.worlds()) {
            if(world.automatic()&&!world.get("trigger").equals("after_exit")) manager.backup(world,"before_enter");
        }
    }
}
