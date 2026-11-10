package pzcloud;

import java.nio.file.*;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Reproducible fixture/catalog benchmark. Also compiles against the 1.0 public API. */
public final class PerformanceProbe {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[1]).toAbsolutePath(); Files.createDirectories(root);
        Config config=new Config(root.resolve("app")); config.set("zomboid",root.resolve("Zomboid").toString()); config.set("setupComplete","true");
        SaveManager manager=new SaveManager(config,s->{});
        if(args[0].equals("prepare")) {
            Path world=config.saves().resolve("Sandbox/Benchmark"); Files.createDirectories(world); byte[] bytes=new byte[1024]; new Random(47).nextBytes(bytes);
            for(int i=0;i<2000;i++) Files.write(world.resolve("map_"+i+".bin"),bytes);
            var w=config.world("Sandbox","Benchmark"); w.set("destination","local");
            for(int i=0;i<3;i++) manager.backup(w,"manual"); return;
        }
        if(args[0].equals("idle")) {
            var worker=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            javax.swing.SwingUtilities.invokeAndWait(()-> { try { new ManagerWindow(manager,worker).setVisible(true); } catch(Exception ex) { throw new RuntimeException(ex); } });
            System.out.println("READY"); System.out.flush(); System.in.read(); System.exit(0);
        }
        manager.history(null); // Warm catalog / one-time 1.x migration excluded from measured repetitions.
        System.gc();
        var bean=ManagementFactory.getThreadMXBean(); long cpu=bean.getCurrentThreadCpuTime(); Map<String,Long> before=io(); long start=System.nanoTime();
        int versions=0; for(int i=0;i<50;i++) versions=manager.history(null).size();
        long elapsed=System.nanoTime()-start,cpuNs=bean.getCurrentThreadCpuTime()-cpu; Map<String,Long> after=io();
        Map<String,Object> result=new LinkedHashMap<>(); result.put("versions",versions); result.put("historyRefreshes",50); result.put("elapsedMs",elapsed/1_000_000.0); result.put("threadCpuMs",cpuNs/1_000_000.0);
        result.put("readCharacters",after.getOrDefault("rchar",0L)-before.getOrDefault("rchar",0L)); result.put("readCalls",after.getOrDefault("syscr",0L)-before.getOrDefault("syscr",0L));
        result.put("heapUsedBytes",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()); System.out.println(Json.encode(result));
    }
    private static Map<String,Long> io() throws Exception {
        Map<String,Long> values=new HashMap<>(); Path p=Path.of("/proc/self/io"); if(!Files.exists(p)) return values;
        for(String line:Files.readAllLines(p)) { String[] parts=line.split(":\\s*",2); if(parts.length==2) values.put(parts[0],Long.parseLong(parts[1])); } return values;
    }
}
