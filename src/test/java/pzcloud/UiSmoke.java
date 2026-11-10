package pzcloud;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** Optional visual smoke test under Xvfb. Uses only fake worlds in a temporary directory. */
public final class UiSmoke {
    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("pzcloud-ui-").toRealPath(); Config c=new Config(temp.resolve("app")); c.set("zomboid",temp.resolve("Zomboid").toString());
        SaveManager manager=new SaveManager(c,s->{}); Path world=c.saves().resolve("Sandbox/Riverside - Santiago"); Files.createDirectories(world); Files.writeString(world.resolve("players.db"),"fixture");
        var w=c.world("Sandbox","Riverside - Santiago"); w.set("destination","local"); w.set("automatic",true); manager.backup(w,"manual");
        Path second=c.saves().resolve("Apocalypse/Muldraugh"); Files.createDirectories(second); Files.writeString(second.resolve("map_ver.bin"),"42.22.1");
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(); CountDownLatch done=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(()-> {
            try {
                ManagerWindow window=new ManagerWindow(manager,worker); window.setVisible(true);
                javax.swing.Timer timer=new javax.swing.Timer(2500,e->{
                    try {
                        String filename=args.length>0?args[0]:"build/ui-smoke.png"; ImageIO.write(new Robot().createScreenCapture(window.getBounds()),"png",Path.of(filename).toFile());
                        window.dispose(); worker.shutdownNow(); done.countDown();
                    } catch(Exception ex) { throw new RuntimeException(ex); }
                }); timer.setRepeats(false); timer.start();
            } catch(Exception ex) { throw new RuntimeException(ex); }
        });
        if(!done.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("UI did not finish startup"); FilesEx.deleteTree(temp); System.out.println("UI smoke passed"); System.exit(0);
    }
}
