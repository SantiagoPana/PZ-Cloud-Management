package pzcloud;

import javax.swing.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class PzCloudApp {
    private PzCloudApp() {}
    public static void main(String[] args) {
        try { launch(args); }
        catch(Exception ex) {
            System.err.println("PZ Cloud Management: "+ex.getMessage());
            if(args.length==0&&!java.awt.GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null,ex.getMessage(),"PZ Cloud Management",JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
    private static void launch(String[] args) throws Exception {
        Path home=Config.defaultHome(); Files.createDirectories(home);
        FileChannel lockChannel=FileChannel.open(home.resolve("manager.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock lock=lockChannel.tryLock();
        if(lock==null) {
            lockChannel.close();
            if(args.length==0||args[0].equals("gui")) { FilesEx.atomicWrite(home.resolve("open.request"),"open"); return; }
            throw new IOException("El gestor ya está abierto. Usá esa ventana o cerralo antes de ejecutar la CLI.");
        }
        Runtime.getRuntime().addShutdownHook(new Thread(()-> { try { lock.release(); lockChannel.close(); } catch(IOException ignored) {} }));
        AtomicReference<ManagerWindow> window=new AtomicReference<>();
        Consumer<String> log=message-> {
            System.out.println(message); var ui=window.get(); if(ui!=null) ui.log(message);
            try {
                Path file=home.resolve("activity.log"); if(Files.exists(file)&&Files.size(file)>2*1024*1024) FilesEx.move(file,home.resolve("activity.previous.log"),true);
                Files.writeString(file,java.time.Instant.now()+" "+message+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            } catch(IOException ignored) { /* A logging failure must not invalidate a completed backup. */ }
        };
        SaveManager manager=new SaveManager(new Config(home),log);
        if(args.length==0||args[0].equals("gui")) {
            ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1,r->{ Thread t=new Thread(r,"pzcloud-worker"); t.setPriority(Thread.MIN_PRIORITY); return t; });
            worker.setRemoveOnCancelPolicy(true); worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            SwingUtilities.invokeAndWait(()-> {
                try { ManagerWindow ui=new ManagerWindow(manager,worker); window.set(ui); ui.setVisible(true); }
                catch(IOException ex) { worker.shutdownNow(); throw new UncheckedIOException(ex); }
            });
            return;
        }
        switch(args[0]) {
            case "list" -> { for(var w:manager.worlds()) System.out.println(w.key()); }
            case "history" -> { for(var l:manager.history(null)) System.out.println(l.snapshot().archive()+" · "+l); }
            case "backup" -> {
                require(args,3,"backup <modo> <partida>"); System.out.println(manager.backup(manager.config.world(args[1],args[2]),"manual").snapshot().archive());
            }
            case "sync" -> manager.sync();
            case "cloud" -> { for(var r:manager.cloud.list()) System.out.println(r.fileId()+" · "+r); }
            case "download" -> {
                require(args,2,"download <id-de-Drive>"); var remote=manager.cloud.list().stream().filter(r->r.fileId().equals(args[1])).findFirst().orElseThrow(()->new IOException("No se encontró la versión."));
                System.out.println(manager.download(remote).snapshot().archive());
            }
            case "restore" -> {
                require(args,3,"restore <respaldo.zip> <nombre-destino> [--replace]"); var local=manager.importArchive(Path.of(args[1]));
                manager.restore(local,local.snapshot().get("mode"),args[2],Arrays.asList(args).contains("--replace"));
            }
            case "login" -> {
                if(args.length>1) manager.auth.importClient(Path.of(args[1])); manager.auth.connect(ManagerWindow::openBrowser); manager.auth.verifyDriveAccess(); manager.config.update(Map.of("cloudMode","drive","setupComplete",true)); System.out.println("Google Drive comprobado.");
            }
            case "check-google" -> { manager.auth.verifyDriveAccess(); System.out.println("Acceso al espacio de respaldos de Google Drive comprobado."); }
            case "logout" -> manager.auth.disconnect();
            case "recover" -> manager.engine.recover(manager.config.saves());
            case "install-mod" -> new GameBridge(manager,()->{},log).installMod();
            default -> throw new IOException("Comandos: gui, list, history, backup, sync, cloud, download, restore, login, check-google, logout, recover, install-mod.");
        }
        lock.release(); lockChannel.close();
    }
    private static void require(String[] args,int length,String usage) throws IOException { if(args.length<length) throw new IOException("Uso: "+usage); }
}
