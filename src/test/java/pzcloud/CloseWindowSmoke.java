package pzcloud;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** Native integration check: the title-bar X must exit, never minimize to the tray. */
public final class CloseWindowSmoke {
    private CloseWindowSmoke() {}
    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("knoxnet-close-x-").toRealPath();
        Config config=new Config(temp.resolve("config"));
        config.set("setupComplete","true");
        config.set("language","es");
        config.set("zomboid",temp.resolve("Zomboid").toString());
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        ManagerWindow[] win=new ManagerWindow[1];

        // Fail the test when the app merely hides in the tray instead of terminating.
        Thread watchdog=new Thread(()-> {
            try { Thread.sleep(8000); } catch(InterruptedException ignored) {}
            System.err.println("FAIL: title-bar X did not terminate Knox Net");
            Runtime.getRuntime().halt(3);
        },"close-window-test-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();

        SwingUtilities.invokeAndWait(()-> {
            try {
                win[0]=new ManagerWindow(new SaveManager(config,message->{}),worker);
                win[0].setVisible(true);
            } catch(Exception ex) { throw new RuntimeException(ex); }
        });
        worker.submit(()->{}).get(5,TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(()-> {
            // A normal user clicks the window's X. This must invoke System.exit(0).
            // The watchdog fails if it is hidden in the system tray instead.
            win[0].dispatchEvent(new WindowEvent(win[0],WindowEvent.WINDOW_CLOSING));
        });
        // System.exit(0) normally terminates during dispatch. This is a hard failure otherwise.
        Thread.sleep(9000);
        throw new AssertionError("Window closing did not exit");
    }
}
