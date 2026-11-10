package pzcloud;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;

/** Version 2: two main tasks, compact models, no timed UI/cloud refresh. */
public final class ManagerWindow extends JFrame implements AutoCloseable {
    private static final long serialVersionUID=2L;
    private static final Color BG=new Color(19,27,28),CARD=new Color(29,40,40),FG=new Color(235,241,231),MUTED=new Color(164,183,175),GREEN=new Color(177,216,135);
    private final SaveManager manager;
    private final ScheduledExecutorService worker;
    private final BackgroundService service;
    private final DefaultListModel<Config.World> worldModel=new DefaultListModel<>();
    private final JList<Config.World> worlds=new JList<>(worldModel);
    private final DefaultListModel<Recovery> recoveryModel=new DefaultListModel<>();
    private final JList<Recovery> recovery=new JList<>(recoveryModel);
    private final JComboBox<String> source=new JComboBox<>(new String[]{"En esta PC","En mi carpeta / nube"});
    private final JLabel selectedName=new JLabel("Elegí una partida"),lastCopy=new JLabel(""),destination=new JLabel(""),status=new JLabel("Listo"),connection=new JLabel();
    private final JCheckBox automatic=new JCheckBox("Guardar automáticamente al cerrar Zomboid");
    private final JTextArea activity=new JTextArea(15,75);
    private final JProgressBar progress=new JProgressBar();
    private final JTabbedPane tabs=new JTabbedPane();
    private final List<JButton> buttons=new ArrayList<>();
    private List<SaveManager.Local> copies=List.of();
    private volatile String selectedKey="";
    private boolean busy,updating,started;
    private TrayIcon tray;
    @FunctionalInterface interface Task<T> { T run() throws Exception; }
    private record Recovery(SaveManager.Local local,CloudProvider.Remote remote) {
        String name() { return local!=null?local.snapshot().get("name"):remote.get("saveName"); }
        String date() { return local!=null?local.snapshot().get("createdAt"):remote.get("created"); }
        String device() { return local!=null?local.snapshot().get("deviceName"):remote.get("device"); }
        long bytes() { return local!=null?local.snapshot().bytes():remote.bytes(); }
        public String toString() { return name()+"   ·   "+friendlyDate(date())+"   ·   "+device()+"   ·   "+FilesEx.human(bytes()); }
    }
    public ManagerWindow(SaveManager manager,ScheduledExecutorService worker) throws IOException {
        super("PZ Cloud · 2.0"); this.manager=manager; this.worker=worker;
        theme(); service=new BackgroundService(manager,worker,this::showApp,()-> { try { loadLocal(); } catch(IOException ex) { log(ex.getMessage()); } },this::log);
        setMinimumSize(new Dimension(900,660)); setSize(1060,740); setLocationRelativeTo(null); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel root=new JPanel(new BorderLayout(0,20)); root.setBorder(new EmptyBorder(25,28,18,28)); setContentPane(root);
        JPanel header=new JPanel(new BorderLayout(12,0)); JPanel titles=vertical();
        JLabel brand=new JLabel("PZ CLOUD  /  2.0"); brand.setForeground(GREEN); titles.add(brand); titles.add(Box.createVerticalStrut(7));
        JLabel title=new JLabel("Tus partidas, a salvo."); title.setFont(new Font("Segoe UI",Font.BOLD,29)); titles.add(title); titles.add(Box.createVerticalStrut(7)); titles.add(muted("Guardá tu progreso. Seguí jugando donde quieras.")); header.add(titles,BorderLayout.WEST);
        JPanel settings=vertical(); connection.setForeground(GREEN); settings.add(connection); settings.add(Box.createVerticalStrut(12)); settings.add(button("Configuración",this::setup)); header.add(settings,BorderLayout.EAST); root.add(header,BorderLayout.NORTH);
        tabs.addTab("Mis partidas",home()); tabs.addTab("Recuperar partida",recoverPanel()); root.add(tabs,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(12,0)); JPanel state=vertical(); state.add(status); progress.setIndeterminate(false); progress.setVisible(false); progress.setPreferredSize(new Dimension(200,5)); state.add(progress); footer.add(state,BorderLayout.CENTER);
        footer.add(button("Ver actividad",()->JOptionPane.showMessageDialog(this,new JScrollPane(activity),"Actividad",JOptionPane.PLAIN_MESSAGE)),BorderLayout.EAST); root.add(footer,BorderLayout.SOUTH);
        activity.setEditable(false); activity.setLineWrap(true); activity.setWrapStyleWord(true); activity.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        worlds.addListSelectionListener(e->{ if(!e.getValueIsAdjusting()&&!updating) { var w=worlds.getSelectedValue(); selectedKey=w==null?"":w.key(); showSelected(); } });
        automatic.addActionListener(e->{ var w=worlds.getSelectedValue(); if(w==null||updating) return; boolean enabled=automatic.isSelected();
            work("Guardando preferencia…",()-> { w.update(Map.of("automatic",enabled,"trigger","after_exit","destination",manager.config.get("cloudMode").equals("none")?"local":"both")); service.settingsChanged(); loadLocal(); return null; },v->{});
        });
        tabs.addChangeListener(e->{ if(started&&tabs.getSelectedIndex()==1) loadRecovery(); });
        addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { closeWindow(); } });
        SwingUtilities.updateComponentTreeUI(this); installTray(); updateConnection();
        work("Buscando tus partidas…",()-> { if(!manager.guard.running()) manager.engine.recover(manager.config.saves()); service.start(); loadLocal(); return null; },v->{ started=true; if(!manager.config.get("setupComplete").equals("true")) setup(); });
    }
    private static void theme() {
        for(String k:List.of("Panel.background","Viewport.background","TabbedPane.background","ScrollPane.background","OptionPane.background","CheckBox.background")) UIManager.put(k,BG);
        for(String k:List.of("Label.foreground","List.foreground","TextArea.foreground","TextField.foreground","CheckBox.foreground","ComboBox.foreground","TabbedPane.foreground","OptionPane.messageForeground","Button.foreground")) UIManager.put(k,FG);
        for(String k:List.of("List.background","TextArea.background","TextField.background","ComboBox.background","Button.background","TabbedPane.selected")) UIManager.put(k,CARD);
        UIManager.put("List.selectionBackground",new Color(56,83,61)); UIManager.put("List.selectionForeground",FG); UIManager.put("TextField.caretForeground",GREEN);
        for(String k:List.of("Label.font","Button.font","List.font","CheckBox.font","ComboBox.font","TextField.font","TabbedPane.font")) UIManager.put(k,new Font("Segoe UI",Font.PLAIN,14));
    }
    private static JPanel vertical() {
        JPanel p=new JPanel() { private static final long serialVersionUID=1L;
            protected void addImpl(Component component,Object constraints,int index) { if(component instanceof JComponent child) child.setAlignmentX(Component.LEFT_ALIGNMENT); super.addImpl(component,constraints,index); }
        }; p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS)); return p;
    }
    private static JLabel muted(String text) { JLabel l=new JLabel(text); l.setForeground(MUTED); return l; }
    private JPanel row(JComponent... components) { JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,6)); for(var c:components) p.add(c); p.setMaximumSize(new Dimension(Integer.MAX_VALUE,p.getPreferredSize().height)); return p; }
    private JButton button(String label,Runnable action) { JButton b=new JButton(label); b.setMargin(new Insets(11,16,11,16)); b.setFocusPainted(false); b.addActionListener(e->action.run()); buttons.add(b); return b; }
    private JButton dialogButton(String label,Runnable action) { JButton b=new JButton(label); b.setMargin(new Insets(9,12,9,12)); b.setFocusPainted(false); b.addActionListener(e->action.run()); return b; }
    private JButton primary(String label,Runnable action) { JButton b=button(label,action); b.setBackground(GREEN); b.setForeground(BG); b.setFont(b.getFont().deriveFont(Font.BOLD,16)); return b; }
    private JPanel home() {
        JPanel panel=new JPanel(new BorderLayout(20,10)); panel.setBorder(new EmptyBorder(20,0,0,0)); worlds.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); worlds.setFixedCellHeight(62);
        worlds.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID=1L;
            public Component getListCellRendererComponent(JList<?> l,Object value,int i,boolean selected,boolean focus) {
                JLabel label=(JLabel)super.getListCellRendererComponent(l,value,i,selected,focus); if(value instanceof Config.World w) label.setText(w.name+"   ·   "+w.mode);
                label.setBorder(new EmptyBorder(12,14,12,10)); return label;
            }
        });
        JScrollPane list=new JScrollPane(worlds); list.setPreferredSize(new Dimension(300,400)); panel.add(list,BorderLayout.WEST);
        JPanel detail=vertical(); detail.setBorder(new EmptyBorder(14,8,10,5)); selectedName.setFont(new Font("Segoe UI",Font.BOLD,24)); detail.add(selectedName); detail.add(Box.createVerticalStrut(15)); lastCopy.setForeground(MUTED); detail.add(lastCopy); detail.add(Box.createVerticalStrut(8)); detail.add(destination); detail.add(Box.createVerticalStrut(20));
        detail.add(row(primary("Guardar partida",this::save),button("Jugar",()->work("Preparando Zomboid…",()-> { service.monitor().beforeLaunch(); openBrowser(URI.create("steam://rungameid/108600")); return null; },v->{}))));
        detail.add(Box.createVerticalStrut(16)); detail.add(automatic); detail.add(Box.createVerticalStrut(8)); detail.add(muted("La copia automática espera a que cierres el juego por completo.")); detail.add(Box.createVerticalStrut(16));
        detail.add(row(button("Versiones anteriores…",()-> { source.setSelectedIndex(0); tabs.setSelectedIndex(1); loadRecovery(); }),button("Actualizar partidas",()->work("Actualizando…",()-> { loadLocal(); return null; },v->{}))));
        detail.add(Box.createVerticalStrut(14)); detail.add(muted("Tu progreso actual se respalda antes de recuperar otra copia.")); JScrollPane details=new JScrollPane(detail); details.setBorder(null); details.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); panel.add(details,BorderLayout.CENTER); return panel;
    }
    private JPanel recoverPanel() {
        JPanel panel=new JPanel(new BorderLayout(0,14)); panel.setBorder(new EmptyBorder(20,0,0,0)); source.addActionListener(e->{ if(started&&tabs.getSelectedIndex()==1) loadRecovery(); });
        panel.add(row(new JLabel("Buscar copias:"),source,button("Actualizar lista",this::loadRecovery)),BorderLayout.NORTH); recovery.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); recovery.setFixedCellHeight(56); panel.add(new JScrollPane(recovery),BorderLayout.CENTER);
        panel.add(row(primary("Recuperar esta partida",()-> { Recovery item=recovery.getSelectedValue(); if(item==null) { message("Elegí la copia que querés recuperar."); return; }
            if(item.local()!=null) restore(item.local()); else work("Trayendo la copia a esta PC…",()->manager.download(item.remote()),this::restore);
        }),button("Importar copia…",this::importZip),button("Más opciones…",this::copyOptions)),BorderLayout.SOUTH); return panel;
    }
    private Config.World selected() { var w=worlds.getSelectedValue(); if(w==null) message("Primero elegí una partida. Si no aparece, revisá la carpeta del juego en Configuración."); return w; }
    private void save() { var w=selected(); if(w==null) return;
        work("Guardando y verificando tu partida…",()-> { w.set("destination",manager.config.get("cloudMode").equals("none")?"local":"both"); var local=manager.backup(w,"manual"); loadLocal(); service.changed(); return local; },local->{
            status.setText(manager.canSync()?"Copia local lista. Se enviará cuando el juego esté cerrado.":"Copia guardada en esta PC.");
        });
    }
    private void loadLocal() throws IOException {
        List<Config.World> list=manager.worlds(); List<SaveManager.Local> history=manager.history(null);
        SwingUtilities.invokeLater(()-> { updating=true; copies=history; worldModel.clear(); list.forEach(worldModel::addElement);
            var select=list.stream().filter(w->w.key().equals(selectedKey)).findFirst().orElse(list.isEmpty()?null:list.get(0)); if(select!=null) { worlds.setSelectedValue(select,true); selectedKey=select.key(); }
            updating=false; showSelected(); updateConnection();
        });
    }
    private void showSelected() {
        var w=worlds.getSelectedValue(); updating=true;
        if(w==null) { selectedName.setText("Todavía no hay partidas"); lastCopy.setText("Jugá una vez o recuperá una copia de otra PC."); automatic.setEnabled(false); destination.setText(""); }
        else { selectedName.setText(w.name); var last=copies.stream().filter(c->c.snapshot().get("mode").equals(w.mode)&&c.snapshot().get("name").equals(w.name)).findFirst();
            lastCopy.setText(last.map(c->"Última copia: "+friendlyDate(c.snapshot().get("createdAt"))+" · "+delivery(c.status())).orElse("Todavía no hiciste una copia de esta partida."));
            destination.setText(switch(manager.config.get("cloudMode")) { case "folder"->"Destino: esta PC y tu carpeta sincronizada"; case "drive"->"Destino: esta PC y Google Drive"; default->"Destino: esta PC"; }); automatic.setSelected(w.automatic()&&!w.get("trigger").equals("before_enter")); automatic.setEnabled(!busy);
        } updating=false;
    }
    private static String delivery(String state) { return switch(state) { case "synced"->"en Google Drive"; case "folder"->"en tu carpeta"; case "pending"->"pendiente de enviar"; default->"en esta PC"; }; }
    private void loadRecovery() {
        if(busy) return;
        boolean remote=source.getSelectedIndex()==1;
        if(remote&&!manager.canSync()) { recoveryModel.clear(); message("Configurá tu carpeta sincronizada o conectá Google Drive para buscar copias de otra PC."); return; }
        work(remote?"Buscando tus copias afuera…":"Leyendo tus copias…",()-> {
            List<Recovery> result=new ArrayList<>(); if(remote) for(var r:manager.cloud.list()) result.add(new Recovery(null,r)); else for(var c:manager.history(null)) result.add(new Recovery(c,null)); return result;
        },items->{ recoveryModel.clear(); items.forEach(recoveryModel::addElement); if(!items.isEmpty()) recovery.setSelectedIndex(0); else status.setText("Todavía no hay copias en este lugar."); });
    }
    private void restore(SaveManager.Local local) {
        String original=local.snapshot().get("name"); JTextField name=new JTextField(original+" (recuperada)",28);
        JPanel p=vertical(); p.add(new JLabel("Recuperar «"+original+"»")); p.add(Box.createVerticalStrut(10)); p.add(new JLabel("Copia del "+friendlyDate(local.snapshot().get("createdAt")))); p.add(Box.createVerticalStrut(16)); p.add(new JLabel("Nombre en esta PC:")); p.add(name); p.add(Box.createVerticalStrut(12));
        p.add(muted("Usá un nombre nuevo para conservar ambas partidas.")); p.add(muted("Si el nombre ya existe, primero se respalda su progreso.")); p.add(muted("Necesitás la misma versión de PZ y los mismos mods."));
        if(JOptionPane.showConfirmDialog(this,p,"Recuperar partida",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        String target=name.getText().trim(); work("Recuperando tu partida…",()-> { var result=manager.restore(local,local.snapshot().get("mode"),target,true); loadLocal(); service.changed(); return result; },v->message("Listo. Abrí Zomboid y buscá «"+target+"» en Cargar partida."));
    }
    private void setup() {
        JTextField folder=new JTextField(manager.config.get("syncFolder"),36); folder.setEditable(false);
        JRadioButton local=new JRadioButton("Solo en esta PC"),synced=new JRadioButton("En una carpeta de Drive u OneDrive que ya uso"),direct=new JRadioButton("Google Drive directo");
        ButtonGroup group=new ButtonGroup(); for(var r:List.of(local,synced,direct)) { group.add(r); r.setBackground(BG); r.setForeground(FG); }
        switch(manager.config.get("cloudMode")) { case "folder"->synced.setSelected(true); case "drive"->direct.setSelected(true); default->local.setSelected(true); }
        JButton choose=new JButton("Elegir carpeta…"); choose.addActionListener(e->{ Path p=chooseFolder(); if(p!=null) { String leaf=p.getFileName()==null?"":p.getFileName().toString(); folder.setText((leaf.equals("PZCloudManagement")?p:p.resolve("PZCloudManagement")).toString()); synced.setSelected(true); } });
        JPanel p=vertical(); JLabel title=new JLabel("¿Dónde querés guardar tus copias?"); title.setFont(title.getFont().deriveFont(Font.BOLD,21)); p.add(title); p.add(Box.createVerticalStrut(15)); p.add(local); p.add(Box.createVerticalStrut(12)); p.add(synced); p.add(row(folder,choose)); p.add(muted("Elegí una carpeta que tu aplicación de nube ya sincronice.")); p.add(muted("Esperá a que esa aplicación confirme la subida antes de cambiar de PC.")); p.add(Box.createVerticalStrut(14)); p.add(direct); p.add(muted("Reutiliza tu conexión de la 1.0. La primera vez necesita un archivo de conexión."));
        p.add(row(dialogButton("Conectar Google…",this::connectDrive),dialogButton("Más opciones…",this::advanced))); p.add(Box.createVerticalStrut(8)); p.add(muted("Al recuperar en otra PC, elegí allí la misma carpeta o cuenta."));
        if(JOptionPane.showConfirmDialog(this,p,"Configuración sencilla",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;
        String mode=synced.isSelected()?"folder":direct.isSelected()?"drive":"none",path=folder.getText();
        if(mode.equals("folder")&&path.isBlank()) { message("Elegí la carpeta donde querés guardar las copias."); return; }
        work("Preparando tus copias…",()-> {
            if(mode.equals("folder")) { Path target=Path.of(path); FilesEx.separate(manager.config.home,target); FilesEx.separate(manager.config.saves(),target); Files.createDirectories(target); }
            manager.config.update(Map.of("cloudMode",mode,"syncFolder",path,"setupComplete",true));
            for(var w:manager.worlds()) if(w.automatic()) w.set("destination",mode.equals("none")?"local":"both");
            service.settingsChanged(); loadLocal(); return null;
        },v->{ if(mode.equals("drive")&&!manager.auth.connected()) status.setText("Conectá Google Drive desde Configuración para enviar tus copias."); });
    }
    private void connectDrive() {
        Path client=null;
        if(!manager.auth.configured()) { JFileChooser c=new JFileChooser(); c.setDialogTitle("Elegí tu archivo de conexión de Google"); c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivo de conexión de Google","json")); if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return; client=c.getSelectedFile().toPath(); }
        Path selectedClient=client;
        work("Terminá la conexión en tu navegador…",()-> { if(selectedClient!=null) manager.auth.importClient(selectedClient); manager.auth.connect(ManagerWindow::openBrowser); manager.config.update(Map.of("cloudMode","drive","setupComplete",true)); service.settingsChanged(); loadLocal(); return null; },v->message("Google Drive conectado. Ya podés guardar y recuperar tus partidas."));
    }
    private Path chooseFolder() { JFileChooser c=new JFileChooser(); c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); return c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION?c.getSelectedFile().toPath():null; }
    private void advanced() {
        JPanel p=vertical(); p.add(muted("Estas opciones no hacen falta para guardar y recuperar partidas."));
        p.add(row(dialogButton("Cambiar carpeta Zomboid…",()-> { Path target=chooseFolder(); if(target!=null) work("Cambiando carpeta…",()-> { manager.guard.requireClosed(); FilesEx.separate(target.resolve("Saves"),manager.config.home); manager.config.set("zomboid",target.toString()); loadLocal(); return null; },v->message("Carpeta actualizada. Reiniciá el gestor para actualizar el botón dentro del juego.")); }),dialogButton("Instalar botón en el juego",()->work("Instalando…",()-> { service.bridge().installMod(); return null; },v->message("Activá PZ Cloud Management en el menú Mods de Zomboid.")))));
        p.add(row(dialogButton("Abrir mis copias",()-> { try { Desktop.getDesktop().open(manager.config.archives().toFile()); } catch(IOException e) { message(e.getMessage()); } }),dialogButton("Ayuda de Google Drive",()->openBrowser(URI.create("https://github.com/SantiagoPana/PZ-Cloud-Management/blob/development/docs/GOOGLE_DRIVE.md")))));
        p.add(row(dialogButton("Desconectar Google",()-> { if(JOptionPane.showConfirmDialog(this,"Las copias remotas se conservan. Las nuevas subidas irán a la próxima cuenta conectada.","Desconectar",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) work("Desconectando…",()-> { manager.auth.disconnect(); service.settingsChanged(); return null; },v->updateConnection()); }),dialogButton("Cerrar el gestor por completo",this::exit)));
        JOptionPane.showMessageDialog(this,p,"Más opciones",JOptionPane.PLAIN_MESSAGE);
    }
    private void importZip() { JFileChooser c=new JFileChooser(); c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Copia de PZ Cloud","zip")); if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) work("Verificando tu copia…",()->manager.importArchive(c.getSelectedFile().toPath()),this::restore); }
    private void copyOptions() {
        Recovery item=recovery.getSelectedValue(); if(item==null) { message("Elegí una copia primero."); return; }
        Object[] choices=item.local()!=null?new Object[]{"Exportar ZIP","Eliminar copia","Cancelar"}:new Object[]{"Eliminar copia remota","Cancelar"};
        int choice=JOptionPane.showOptionDialog(this,"La partida actual no se elimina con estas opciones.","Más opciones de copia",JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,choices,choices[choices.length-1]);
        if(item.local()!=null&&choice==0) { JFileChooser c=new JFileChooser(); c.setSelectedFile(new File(item.name()+".zip")); if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return; Path target=c.getSelectedFile().toPath();
            if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"El archivo existe. ¿Reemplazarlo?","Exportar copia",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
            work("Exportando copia…",()-> { FilesEx.separate(manager.config.archives(),target); manager.engine.verify(item.local().snapshot().archive(),item.local().snapshot().sha256()); Path temp=Files.createTempFile(target.toAbsolutePath().getParent(),".export-",".part"); try { Files.copy(item.local().snapshot().archive(),temp,StandardCopyOption.REPLACE_EXISTING); manager.engine.verify(temp,item.local().snapshot().sha256()); FilesEx.move(temp,target,true); } finally { Files.deleteIfExists(temp); } return null; },v->status.setText("Copia exportada."));
        } else if(item.local()!=null&&choice==1||item.local()==null&&choice==0) {
            if(JOptionPane.showConfirmDialog(this,"¿Eliminar definitivamente esta copia?","Eliminar copia",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION) return;
            work("Eliminando copia…",()-> { if(item.local()!=null) manager.deleteLocal(item.local()); else manager.deleteCloud(item.remote()); loadLocal(); return null; },v->loadRecovery());
        }
    }
    private <T> void work(String label,Task<T> action,Consumer<T> success) {
        if(busy) return; busy=true; buttons.forEach(b->b.setEnabled(false)); automatic.setEnabled(false); source.setEnabled(false); progress.setVisible(true); progress.setIndeterminate(true); log(label);
        worker.execute(()-> { T result=null; Exception error=null; try { result=action.run(); } catch(Exception ex) { error=ex; } T value=result; Exception failure=error;
            SwingUtilities.invokeLater(()-> { busy=false; buttons.forEach(b->b.setEnabled(true)); automatic.setEnabled(worlds.getSelectedValue()!=null); source.setEnabled(true); progress.setIndeterminate(false); progress.setVisible(false); updateConnection();
                if(failure!=null) { log("No se completó: "+failure.getMessage()); JOptionPane.showMessageDialog(this,failure.getMessage(),"No se completó la operación",JOptionPane.ERROR_MESSAGE); }
                else { status.setText("Listo"); success.accept(value); }
            });
        });
    }
    private void updateConnection() { connection.setText(switch(manager.config.get("cloudMode")) { case "folder"->"Carpeta sincronizada"; case "drive"->manager.auth.connected()?"Google Drive conectado":"Google Drive sin conectar"; default->"Copias en esta PC"; }); if(manager.pendingCount()>0) connection.setText(connection.getText()+" · "+manager.pendingCount()+" pendientes"); }
    public void log(String text) { SwingUtilities.invokeLater(()-> { status.setText(text); activity.append(LocalTime.now().withNano(0)+"  "+text+"\n"); if(activity.getDocument().getLength()>16000) activity.setText(activity.getText().substring(8000)); activity.setCaretPosition(activity.getDocument().getLength()); }); }
    private void message(String text) { JOptionPane.showMessageDialog(this,text,"PZ Cloud",JOptionPane.INFORMATION_MESSAGE); }
    public void showApp() { SwingUtilities.invokeLater(()-> { setVisible(true); setState(Frame.NORMAL); toFront(); }); }
    static void openBrowser(URI uri) { try { if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) { Desktop.getDesktop().browse(uri); return; } } catch(IOException ignored) {} SwingUtilities.invokeLater(()->JOptionPane.showInputDialog(null,"Abrí esta dirección en tu navegador:",uri.toString())); }
    private static String friendlyDate(String text) { try { return DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(text)); } catch(Exception e) { return text; } }
    private void closeWindow() { if(manager.config.automaticExits()||manager.hasPending()&&manager.canSync()) { setVisible(false); dispose(); if(tray!=null) tray.displayMessage("PZ Cloud","La protección sigue en segundo plano. Usá Salir para detenerla.",TrayIcon.MessageType.INFO); } else exit(); }
    private void exit() { if(busy) { message("Esperá a que termine la operación antes de salir."); return; } close(); System.exit(0); }
    public void close() { try { service.close(); } catch(IOException ignored) {} worker.shutdownNow(); if(tray!=null) SystemTray.getSystemTray().remove(tray); dispose(); }
    private void installTray() {
        if(!SystemTray.isSupported()) return;
        try { var image=new java.awt.image.BufferedImage(24,24,java.awt.image.BufferedImage.TYPE_INT_ARGB); var g=image.createGraphics(); g.setColor(GREEN); g.fillRoundRect(0,0,24,24,6,6); g.setColor(BG); g.setFont(new Font("SansSerif",Font.BOLD,18)); g.drawString("Z",6,19); g.dispose(); PopupMenu menu=new PopupMenu(); MenuItem show=new MenuItem("Abrir PZ Cloud"); show.addActionListener(e->showApp()); menu.add(show); MenuItem quit=new MenuItem("Salir"); quit.addActionListener(e->SwingUtilities.invokeLater(this::exit)); menu.add(quit); tray=new TrayIcon(image,"PZ Cloud 2.0",menu); tray.setImageAutoSize(true); tray.addActionListener(e->showApp()); SystemTray.getSystemTray().add(tray); } catch(AWTException ignored) {}
    }
}
