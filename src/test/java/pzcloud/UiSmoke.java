package pzcloud;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Real Swing clicks: configure, save and recover an isolated fake world. */
public final class UiSmoke {
    private static ManagerWindow window;
    private static <T extends Component> T find(Container root,Class<T> type,Predicate<T> match) {
        for(Component child:root.getComponents()) {
            if(type.isInstance(child)&&match.test(type.cast(child))) return type.cast(child);
            if(child instanceof Container nested) { T found=find(nested,type,match); if(found!=null) return found; }
        }
        return null;
    }
    private static JButton button(String text) { return find(window,JButton.class,b->b.getText().equals(text)); }
    private static void drain(ScheduledExecutorService worker) throws Exception {
        long until=System.nanoTime()+10_000_000_000L;
        while(System.nanoTime()<until) {
            worker.submit(()->{}).get(10,TimeUnit.SECONDS); var ready=new java.util.concurrent.atomic.AtomicBoolean();
            SwingUtilities.invokeAndWait(()->ready.set(button("Guardar partida").isEnabled())); if(ready.get()) return; Thread.sleep(25);
        }
        throw new AssertionError("UI did not finish its operation");
    }
    private static JDialog dialog() throws Exception {
        long until=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<until) {
            AtomicReference<JDialog> current=new AtomicReference<>(); SwingUtilities.invokeAndWait(()-> { for(Window w:Window.getWindows()) if(w instanceof JDialog d&&d.isShowing()) current.set(d); });
            if(current.get()!=null) return current.get(); Thread.sleep(25);
        }
        throw new AssertionError("Expected dialog did not appear");
    }
    private static void accept(JDialog dialog) throws Exception {
        SwingUtilities.invokeAndWait(()-> { JOptionPane pane=find(dialog,JOptionPane.class,p->true); if(pane==null) throw new AssertionError("No option pane"); pane.setValue(JOptionPane.OK_OPTION); });
    }
    private static void image(Path output) throws Exception {
        SwingUtilities.invokeAndWait(()-> { try { var bitmap=new java.awt.image.BufferedImage(window.getWidth(),window.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB); var g=bitmap.createGraphics(); window.paint(g); g.dispose(); ImageIO.write(bitmap,"png",output.toFile()); } catch(Exception ex) { throw new RuntimeException(ex); } });
    }
    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("pzcloud-ui-").toRealPath(); Config c=new Config(temp.resolve("app")); c.set("zomboid",temp.resolve("Zomboid").toString()); c.set("setupComplete","true"); c.set("language","es");
        SaveManager manager=new SaveManager(c,s->{}); Path world=c.saves().resolve("Sandbox/Riverside - Santiago"); Files.createDirectories(world); Files.writeString(world.resolve("players.db"),"fixture");
        var w=c.world("Sandbox","Riverside - Santiago"); w.set("destination","local"); manager.backup(w,"manual");
        Path second=c.saves().resolve("Apocalypse/Muldraugh"); Files.createDirectories(second); Files.writeString(second.resolve("map_ver.bin"),"42.22.1");
        ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
        try {
            SwingUtilities.invokeAndWait(()-> { try { window=new ManagerWindow(manager,worker); window.setVisible(true); } catch(Exception ex) { throw new RuntimeException(ex); } }); drain(worker);
            // Explicit minimize action must work with or without an OS tray; it must never strand the UI.
            SwingUtilities.invokeAndWait(()-> {
                JButton minimize=button("Minimizar a la bandeja");
                if(minimize==null||!minimize.isEnabled()) throw new AssertionError("Accessible minimize-to-tray button missing");
                minimize.doClick();
                if(window.trayAvailable()&&window.isVisible()) throw new AssertionError("Window was not hidden in the system tray");
                if(!window.trayAvailable()&&!window.isVisible()) throw new AssertionError("Missing tray left the app inaccessible");
            });
            window.showApp();
            SwingUtilities.invokeAndWait(()-> {
                if(!window.isVisible()||window.getState()!=Frame.NORMAL) throw new AssertionError("Window failed to return from the tray");
            });
            // Change the language without restarting. The user's worlds and Google settings must survive.
            SwingUtilities.invokeAndWait(()-> {
                window.applyLanguage("en");
                if(button("Back up save")==null||button("Settings")==null||button("Minimize to tray")==null)
                    throw new AssertionError("English UI not applied immediately");
                if(!"en".equals(c.get("language")) || !"none".equals(c.get("cloudMode")))
                    throw new AssertionError("Language change modified backup settings");
                window.applyLanguage("es");
                if(button("Guardar partida")==null||button("Configuración")==null)
                    throw new AssertionError("Spanish UI not restored");
                if(!"es".equals(c.get("language")))
                    throw new AssertionError("Language preference not persisted");
            });
            // Native dialog is interacted with through Swing, using the same actions as a user.
            SwingUtilities.invokeLater(()->button("Configuración").doClick()); JDialog config=dialog();
            SwingUtilities.invokeAndWait(()-> { var radio=find(config,JRadioButton.class,b->b.getText().equals("Solo en esta PC")); if(radio==null) throw new AssertionError("No simple local option"); radio.setSelected(true); });
            SwingUtilities.invokeLater(()->find(config,JButton.class,b->b.getText().equals("Más opciones…")).doClick()); JDialog advanced=dialog();
            SwingUtilities.invokeAndWait(()-> { if(find(advanced,JButton.class,b->b.getText().contains("autor")||b.getText().contains("JSON"))!=null) throw new AssertionError("Advanced settings must not expose publisher configuration"); }); accept(advanced); accept(config); drain(worker);
            if(!c.get("cloudMode").equals("none")) throw new AssertionError("Configuration was not applied");
            SwingUtilities.invokeAndWait(()-> { var search=find(window,JTextField.class,t->true); search.setText("No existe esta partida"); if(button("Guardar partida").isEnabled()||button("Jugar").isEnabled()) throw new AssertionError("Empty search must disable actions for missing worlds"); search.setText("Riverside"); if(!button("Guardar partida").isEnabled()) throw new AssertionError("Search should select the matching world"); search.setText(""); });
            int before=manager.history(null).size(); SwingUtilities.invokeAndWait(()->button("Guardar partida").doClick()); drain(worker);
            if(manager.history(null).size()!=before+1) throw new AssertionError("Save button did not create a verified backup");
            Path mainImage=Path.of(args.length>0?args[0]:"build/ui-smoke.png"); image(mainImage);
            SwingUtilities.invokeAndWait(()->find(window,JTabbedPane.class,p->true).setSelectedIndex(1)); drain(worker); image(Path.of("build/ui-v2-recover.png"));
            SwingUtilities.invokeLater(()->button("Recuperar esta partida").doClick()); JDialog restore=dialog();
            SwingUtilities.invokeAndWait(()->find(restore,JTextField.class,t->true).setText("Recuperada desde interfaz")); accept(restore);
            // The success notice is modal; acknowledge it before draining the EDT.
            JDialog done=dialog(); accept(done); drain(worker);
            Path recovered=c.saves().resolve("Sandbox/Recuperada desde interfaz/players.db");
            if(!Files.exists(recovered)||!Files.readString(recovered).equals("fixture")) throw new AssertionError("Recovery button did not install the selected world");
            System.out.println("UI smoke passed: minimize/restore from tray, setup, cached search, empty-selection actions, save and restore; original progress retained");
        } finally { if(window!=null) SwingUtilities.invokeAndWait(window::close); else worker.shutdownNow(); FilesEx.deleteTree(temp); }
        System.exit(0);
    }
}
