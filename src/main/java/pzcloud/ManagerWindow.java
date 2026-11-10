package pzcloud;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/** Native desktop UI. File hashing/network operations always run off the Swing thread. */
public final class ManagerWindow extends JFrame {
    private static final long serialVersionUID=1L;
    private static final Color BG=new Color(20,27,28), CARD=new Color(30,40,41), FG=new Color(231,239,231), MUTED=new Color(169,187,180), ACCENT=new Color(167,209,116);
    private final SaveManager manager;
    private final ScheduledExecutorService worker;
    private final SessionMonitor monitor;
    private final GameBridge bridge;
    private final DefaultListModel<Config.World> worldsModel=new DefaultListModel<>();
    private final DefaultListModel<SaveManager.Local> localModel=new DefaultListModel<>();
    private final JList<Config.World> worlds=new JList<>(worldsModel);
    private final JList<SaveManager.Local> history=new JList<>(localModel);
    private final DefaultTableModel cloudModel=new DefaultTableModel(new String[]{"Partida","Modo","Fecha (UTC)","Equipo","Tamaño","Historial"},0) {
        private static final long serialVersionUID=1L;
        public boolean isCellEditable(int row,int col) { return false; }
    };
    private final JTable cloudTable=new JTable(cloudModel);
    private final JTextArea activity=new JTextArea(5,60);
    private final JLabel status=new JLabel("Listo"), account=new JLabel(), localDetails=new JLabel("Elegí una partida para ver sus versiones.");
    private final JProgressBar progress=new JProgressBar();
    private final JTabbedPane tabs=new JTabbedPane();
    private final List<JButton> buttons=new ArrayList<>();
    private List<CloudProvider.Remote> cloudVersions=List.of();
    private boolean updating,busy;
    private long lastSync,lastCloud;
    private String lastAutoError="";
    @FunctionalInterface interface Job { void run() throws Exception; }

    public ManagerWindow(SaveManager manager,ScheduledExecutorService worker) throws IOException {
        super("PZ Cloud Management"); this.manager=manager; this.worker=worker;
        monitor=new SessionMonitor(manager,this::log); bridge=new GameBridge(manager,this::showApp,this::log);
        theme(); setMinimumSize(new Dimension(980,700)); setSize(1180,800); setLocationRelativeTo(null); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel root=new JPanel(new BorderLayout(0,18)); root.setBorder(new EmptyBorder(24,28,20,28)); setContentPane(root);
        JPanel heading=new JPanel(new BorderLayout());
        JPanel title=new JPanel(new GridLayout(0,1,0,6)); JLabel name=new JLabel("PZ CLOUD MANAGEMENT"); name.setFont(new Font("Segoe UI",Font.BOLD,27)); name.setForeground(ACCENT);
        title.add(name); title.add(new JLabel("Tus mundos, donde sigas jugando.")); heading.add(title,BorderLayout.WEST);
        JPanel top=new JPanel(new GridLayout(0,1,0,8)); account.setHorizontalAlignment(SwingConstants.RIGHT); top.add(account);
        top.add(button("Respaldar y abrir Zomboid",()->work("Preparando partida…",()-> {
            monitor.beforeLaunch(); openBrowser(URI.create("steam://rungameid/108600")); log("Steam recibió la solicitud de inicio. Los respaldos de salida se harán al cerrar Zomboid.");
        }))); heading.add(top,BorderLayout.EAST); root.add(heading,BorderLayout.NORTH);
        tabs.addTab("Mis partidas",localPanel()); tabs.addTab("Google Drive",cloudPanel()); tabs.addTab("Configuración",settingsPanel()); root.add(tabs,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(0,8)); JPanel bar=new JPanel(new BorderLayout(12,0)); bar.add(status,BorderLayout.CENTER); progress.setPreferredSize(new Dimension(140,8)); bar.add(progress,BorderLayout.EAST); footer.add(bar,BorderLayout.NORTH);
        activity.setEditable(false); activity.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); activity.setLineWrap(true); activity.setWrapStyleWord(true); footer.add(new JScrollPane(activity),BorderLayout.CENTER); root.add(footer,BorderLayout.SOUTH);
        addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { closeChoice(); } });
        worlds.addListSelectionListener(e->{ if(!e.getValueIsAdjusting()&&!updating) refreshLocal(); });
        history.addListSelectionListener(e->{
            var local=history.getSelectedValue();
            if(local!=null) localDetails.setText("Build declarada: "+local.snapshot().get("gameBuild")+" · "+local.snapshot().get("deviceName")+" · "+local.snapshot().get("reason"));
        });
        SwingUtilities.updateComponentTreeUI(this); installTray(); updateAccount();
        worker.scheduleWithFixedDelay(this::background,2,2,TimeUnit.SECONDS);
        work("Buscando partidas…",()-> { if(!manager.guard.running()) manager.engine.recover(manager.config.saves()); refreshLocalData(); });
    }
    public void showApp() { SwingUtilities.invokeLater(()-> { setVisible(true); setState(Frame.NORMAL); toFront(); requestFocus(); }); }
    private static void theme() {
        for(String key:List.of("Panel.background","Viewport.background","TabbedPane.background","ScrollPane.background","OptionPane.background")) UIManager.put(key,BG);
        for(String key:List.of("Label.foreground","List.foreground","Table.foreground","TextArea.foreground","OptionPane.messageForeground","CheckBox.foreground","TabbedPane.foreground")) UIManager.put(key,FG);
        for(String key:List.of("List.background","Table.background","TextArea.background","TextField.background","ComboBox.background","Spinner.background","Button.background","CheckBox.background")) UIManager.put(key,CARD);
        UIManager.put("Button.foreground",FG); UIManager.put("TextField.foreground",FG); UIManager.put("ComboBox.foreground",FG); UIManager.put("TextField.caretForeground",ACCENT);
        UIManager.put("List.selectionBackground",new Color(67,87,67)); UIManager.put("List.selectionForeground",FG); UIManager.put("Table.selectionBackground",new Color(67,87,67)); UIManager.put("Table.selectionForeground",FG);
        UIManager.put("Table.gridColor",new Color(49,62,61)); UIManager.put("TabbedPane.selected",CARD); UIManager.put("TabbedPane.contentAreaColor",BG);
        for(String key:List.of("Label.font","Button.font","List.font","Table.font","TextField.font","CheckBox.font","ComboBox.font","TabbedPane.font")) UIManager.put(key,new Font("Segoe UI",Font.PLAIN,14));
    }
    private JButton button(String label,Runnable action) {
        JButton button=new JButton(label); button.setFocusPainted(false); button.setMargin(new Insets(9,14,9,14)); button.addActionListener(e->action.run()); buttons.add(button); return button;
    }
    private JPanel actions(JButton... list) { JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,8)); for(JButton b:list) p.add(b); return p; }
    private JPanel localPanel() {
        JPanel panel=new JPanel(new BorderLayout(0,12)); panel.setBorder(new EmptyBorder(14,0,0,0));
        panel.add(actions(button("Actualizar",this::refreshLocal),button("Respaldar ahora",()-> {
            Config.World w=selectedWorld(); if(w==null) return;
            work("Creando respaldo…",()-> { manager.backup(w,"manual"); refreshLocalData(); if(manager.auth.connected()) { manager.sync(); refreshLocalData(); } });
        }),button("Opciones de partida",this::worldOptions),button("Importar ZIP",this::importZip)),BorderLayout.NORTH);
        worlds.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); worlds.setFixedCellHeight(52); worlds.setBorder(new EmptyBorder(8,8,8,8));
        worlds.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID=1L;
            public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) {
                JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);
                if(value instanceof Config.World w) label.setText(w.name+"   /   "+w.mode+(w.automatic()?"   • Auto":"")); label.setBorder(new EmptyBorder(8,10,8,10)); return label;
            }
        });
        JPanel versions=new JPanel(new BorderLayout(0,8)); localDetails.setForeground(MUTED); versions.add(localDetails,BorderLayout.NORTH);
        history.setFixedCellHeight(43); versions.add(new JScrollPane(history),BorderLayout.CENTER);
        versions.add(actions(button("Subir a Drive",()-> { var local=selectedLocal(); if(local!=null) work("Sincronizando…",()-> { manager.queue(local); manager.sync(); refreshLocalData(); }); }),
            button("Restaurar…",()-> { var local=selectedLocal(); if(local!=null) restoreDialog(local); }),button("Exportar ZIP",this::exportZip),button("Eliminar copia",this::deleteLocal)),BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(worlds),versions); split.setResizeWeight(.32); split.setDividerLocation(330); split.setBorder(null); panel.add(split,BorderLayout.CENTER);
        JLabel hint=new JLabel("Si la lista está vacía, revisá la carpeta Zomboid en Configuración. Las partidas multijugador quedan fuera de esta versión."); hint.setForeground(MUTED); panel.add(hint,BorderLayout.SOUTH); return panel;
    }
    private JPanel cloudPanel() {
        JPanel panel=new JPanel(new BorderLayout(0,12)); panel.setBorder(new EmptyBorder(14,0,0,0));
        panel.add(actions(button("Actualizar nube",()->work("Consultando Drive…",this::refreshCloudData)),button("Subir pendientes",()->work("Subiendo pendientes…",()-> { manager.sync(); refreshLocalData(); refreshCloudData(); })),
            button("Descargar copia",()-> { var r=selectedRemote(); if(r!=null) work("Descargando…",()-> { manager.download(r); refreshLocalData(); }); }),
            button("Descargar y restaurar…",()-> { var r=selectedRemote(); if(r!=null) work("Descargando…",()-> { var local=manager.download(r); refreshLocalData(); SwingUtilities.invokeLater(()->restoreDialog(local)); }); }),
            button("Eliminar de Drive",this::deleteCloud)),BorderLayout.NORTH);
        cloudTable.setRowHeight(40); cloudTable.setAutoCreateRowSorter(true); cloudTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); cloudTable.getTableHeader().setReorderingAllowed(false);
        panel.add(new JScrollPane(cloudTable),BorderLayout.CENTER);
        JLabel note=new JLabel("Cada fila es una versión independiente. «Versiones divergentes» indica que conviene revisar el equipo y la fecha antes de restaurar."); note.setForeground(MUTED); panel.add(note,BorderLayout.SOUTH); return panel;
    }
    private JPanel settingsPanel() {
        JPanel panel=new JPanel(); panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS)); panel.setBorder(new EmptyBorder(22,12,18,12));
        panel.add(new JLabel("Carpeta de usuario de Project Zomboid")); panel.add(Box.createVerticalStrut(8));
        JTextField path=new JTextField(manager.config.zomboid().toString()); path.setMaximumSize(new Dimension(Integer.MAX_VALUE,34)); path.setEditable(false); panel.add(path);
        panel.add(actions(button("Cambiar carpeta…",()-> {
            JFileChooser chooser=new JFileChooser(manager.config.zomboid().toFile()); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if(chooser.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) { Path chosen=chooser.getSelectedFile().toPath(); work("Actualizando carpeta…",()-> { manager.guard.requireClosed(); FilesEx.separate(chosen.resolve("Saves"),manager.config.home); manager.config.set("zomboid",chosen.toAbsolutePath().toString()); SwingUtilities.invokeLater(()->path.setText(chosen.toString())); refreshLocalData(); }); }
        }),button("Abrir respaldos",()->work("Abriendo carpeta…",()->Desktop.getDesktop().open(manager.config.archives().toFile())))));
        panel.add(Box.createVerticalStrut(20)); panel.add(new JLabel("Google Drive"));
        panel.add(actions(button("1. Importar JSON OAuth…",()-> {
            JFileChooser c=new JFileChooser(); c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Cliente OAuth JSON","json"));
            if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) work("Configurando Google…",()-> { manager.auth.importClient(c.getSelectedFile().toPath()); log("Cliente de escritorio importado. Ya podés conectar tu cuenta."); });
        }),button("2. Conectar cuenta",()->work("Esperando autorización en el navegador…",()-> { manager.auth.connect(ManagerWindow::openBrowser); refreshCloudData(); })),button("Desconectar",()-> {
            if(JOptionPane.showConfirmDialog(this,"Se cerrará la sesión local. Tus archivos de Drive se conservan.\nLas futuras subidas irán a la cuenta que conectes después.","Desconectar Google Drive",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) work("Desconectando…",()-> { manager.auth.disconnect(); SwingUtilities.invokeLater(()-> { cloudVersions=List.of(); cloudModel.setRowCount(0); }); });
        })));
        panel.add(new JLabel("Usá el mismo cliente OAuth y la misma cuenta en las dos PCs. Guía: docs/GOOGLE_DRIVE.md."));
        panel.add(Box.createVerticalStrut(22)); panel.add(new JLabel("Juego y mod opcional"));
        JPanel build=new JPanel(new FlowLayout(FlowLayout.LEFT)); JTextField version=new JTextField(manager.config.get("gameBuild"),10); build.add(new JLabel("Build del juego (declarada):")); build.add(version);
        build.add(button("Guardar build",()-> { String value=version.getText().trim(); work("Guardando…",()->manager.config.set("gameBuild",value)); })); panel.add(build);
        panel.add(actions(button("Instalar botón Cloud Saves",()->work("Instalando mod…",bridge::installMod))));
        panel.add(new JLabel("El botón permite consultar y descargar. Restaurá con el juego cerrado desde esta aplicación.")); panel.add(Box.createVerticalStrut(22));
        panel.add(new JLabel("El gestor debe seguir abierto o en segundo plano para respaldar al salir y reintentar subidas."));
        panel.add(new JLabel("Al entrar: usá «Respaldar y abrir Zomboid». Al abrir directamente desde Steam, se aplica el respaldo de salida.")); panel.add(Box.createVerticalGlue()); return panel;
    }
    private Config.World selectedWorld() { Config.World w=worlds.getSelectedValue(); if(w==null) message("Elegí una partida primero."); return w; }
    private SaveManager.Local selectedLocal() { var l=history.getSelectedValue(); if(l==null) message("Elegí una versión del historial."); return l; }
    private CloudProvider.Remote selectedRemote() { int row=cloudTable.getSelectedRow(); if(row<0) { message("Elegí una versión de Google Drive."); return null; } return cloudVersions.get(cloudTable.convertRowIndexToModel(row)); }
    private void message(String text) { JOptionPane.showMessageDialog(this,text,"PZ Cloud Management",JOptionPane.INFORMATION_MESSAGE); }
    public void log(String text) {
        SwingUtilities.invokeLater(()-> {
            status.setText(text); activity.append(LocalTime.now().withNano(0)+"  "+text+"\n");
            if(activity.getDocument().getLength()>25000) activity.setText(activity.getText().substring(10000)); activity.setCaretPosition(activity.getDocument().getLength());
        });
    }
    private void updateAccount() { account.setText(manager.auth.connected()?"● Google Drive conectado":"○ Google Drive sin conectar"); account.setForeground(manager.auth.connected()?ACCENT:MUTED); }
    private void work(String label,Job job) {
        if(busy) return; busy=true; progress.setIndeterminate(true); buttons.forEach(b->b.setEnabled(false)); log(label);
        worker.execute(()-> {
            try { job.run(); SwingUtilities.invokeLater(()->status.setText("Listo")); }
            catch(Exception ex) { log("Error: "+ex.getMessage()); SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(this,ex.getMessage(),"No se completó la operación",JOptionPane.ERROR_MESSAGE)); }
            finally { SwingUtilities.invokeLater(()-> { busy=false; progress.setIndeterminate(false); buttons.forEach(b->b.setEnabled(true)); updateAccount(); }); }
        });
    }
    private void refreshLocal() { work("Leyendo historial…",this::refreshLocalData); }
    private void refreshLocalData() throws IOException {
        final String[] key={null};
        try { SwingUtilities.invokeAndWait(()-> { var w=worlds.getSelectedValue(); key[0]=w==null?null:w.key(); }); }
        catch(Exception e) { throw new IOException("No se pudo actualizar la ventana.",e); }
        List<Config.World> all=manager.worlds(); Config.World selected=all.stream().filter(w->w.key().equals(key[0])).findFirst().orElse(all.isEmpty()?null:all.get(0));
        List<SaveManager.Local> versions=selected==null?manager.history(null):manager.history(selected);
        SwingUtilities.invokeLater(()-> {
            updating=true; worldsModel.clear(); all.forEach(worldsModel::addElement); if(selected!=null) worlds.setSelectedValue(selected,true);
            localModel.clear(); versions.forEach(localModel::addElement); if(!versions.isEmpty()) history.setSelectedIndex(0);
            if(all.isEmpty()) localDetails.setText("No hay partidas locales. Podés importar o descargar una versión de Drive."); updating=false;
        });
    }
    private void refreshCloudData() throws IOException {
        List<CloudProvider.Remote> versions=manager.cloud.list(); Set<String> conflicts=SaveManager.conflictedWorlds(versions); bridge.publish(versions); lastCloud=System.currentTimeMillis();
        SwingUtilities.invokeLater(()-> {
            cloudVersions=versions; cloudModel.setRowCount(0);
            for(var r:versions) cloudModel.addRow(new Object[]{r.get("saveName"),r.get("mode"),r.get("created").replace('T',' ').replace("Z",""),r.get("device"),FilesEx.human(r.bytes()),conflicts.contains(r.get("world"))?"Versiones divergentes":"Conservado"});
        });
        log("Nube actualizada: "+versions.size()+" versiones.");
    }
    private void worldOptions() {
        Config.World w=selectedWorld(); if(w==null) return;
        JCheckBox auto=new JCheckBox("Respaldos automáticos",w.automatic()); JComboBox<String> trigger=new JComboBox<>(new String[]{"Al abrir desde el gestor","Al cerrar Zomboid","Ambos"});
        trigger.setSelectedIndex(switch(w.get("trigger")) { case "before_enter"->0; case "both"->2; default->1; });
        JComboBox<String> destination=new JComboBox<>(new String[]{"Solo local","Google Drive","Local y Google Drive"}); destination.setSelectedIndex(switch(w.get("destination")) { case "local"->0; case "drive"->1; default->2; });
        JSpinner keep=new JSpinner(new SpinnerNumberModel(w.retention(),1,100,1));
        JPanel p=new JPanel(new GridLayout(0,1,6,9)); p.add(new JLabel(w.toString())); p.add(auto); p.add(new JLabel("Cuándo:")); p.add(trigger); p.add(new JLabel("Destino:")); p.add(destination); p.add(new JLabel("Versiones locales a conservar:")); p.add(keep);
        p.add(new JLabel("Drive conserva todas las versiones hasta que las elimines."));
        if(JOptionPane.showConfirmDialog(this,p,"Opciones de partida",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
            boolean enabled=auto.isSelected(); String t=new String[]{"before_enter","after_exit","both"}[trigger.getSelectedIndex()], d=new String[]{"local","drive","both"}[destination.getSelectedIndex()]; int retention=(Integer)keep.getValue();
            work("Guardando opciones…",()-> { w.set("automatic",enabled); w.set("trigger",t); w.set("destination",d); w.set("retention",retention); refreshLocalData(); });
        }
    }
    private void restoreDialog(SaveManager.Local local) {
        if(busy) { SwingUtilities.invokeLater(()->restoreWhenReady(local)); return; } restoreWhenReady(local);
    }
    private void restoreWhenReady(SaveManager.Local local) {
        if(busy) { javax.swing.Timer timer=new javax.swing.Timer(200,e->restoreDialog(local)); timer.setRepeats(false); timer.start(); return; }
        var snap=local.snapshot(); JTextField name=new JTextField(snap.get("name"));
        JPanel p=new JPanel(new GridLayout(0,1,6,9)); p.add(new JLabel("Versión: "+snap.get("createdAt")+" · "+snap.get("deviceName"))); p.add(new JLabel("Build declarada: "+snap.get("gameBuild")+" · Tu build declarada: "+manager.config.get("gameBuild")));
        p.add(new JLabel("Los archivos de mods de Workshop no se descargan con la partida.")); p.add(new JLabel("Nombre de la partida restaurada (cambialo para crear otra copia):")); p.add(name);
        p.add(new JLabel("Si el nombre existe, se guardará primero una copia del progreso actual.")); p.add(new JLabel("Project Zomboid debe estar completamente cerrado."));
        if(JOptionPane.showConfirmDialog(this,p,"Restaurar punto de guardado",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION) return;
        String target=name.getText().trim(),mode=snap.get("mode");
        work("Restaurando partida…",()-> { manager.restore(local,mode,target,true); refreshLocalData(); SwingUtilities.invokeLater(()->message("Partida restaurada. Ya podés abrir Zomboid y cargar «"+target+"».")); });
    }
    private void importZip() {
        JFileChooser c=new JFileChooser(); c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Respaldos PZ Cloud Management","zip"));
        if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) work("Verificando ZIP…",()-> { var local=manager.importArchive(c.getSelectedFile().toPath()); refreshLocalData(); SwingUtilities.invokeLater(()->restoreDialog(local)); });
    }
    private void exportZip() {
        var local=selectedLocal(); if(local==null) return; JFileChooser c=new JFileChooser(); c.setSelectedFile(new File("PZCloud-"+local.snapshot().id()+".zip"));
        if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return; Path target=c.getSelectedFile().toPath();
        if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,"El archivo existe. ¿Reemplazarlo?","Exportar",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        work("Exportando ZIP…",()-> { manager.engine.verify(local.snapshot().archive(),local.snapshot().sha256()); if(FilesEx.canonical(target).startsWith(FilesEx.canonical(manager.config.archives()))) throw new IOException("Exportá el ZIP fuera de la carpeta interna de respaldos.");
            Path temp=Files.createTempFile(target.toAbsolutePath().getParent(),".export-",".part");
            try { Files.copy(local.snapshot().archive(),temp,StandardCopyOption.REPLACE_EXISTING); manager.engine.verify(temp,local.snapshot().sha256()); FilesEx.move(temp,target,true); } finally { Files.deleteIfExists(temp); } log("Respaldo exportado: "+target); });
    }
    private void deleteLocal() {
        var local=selectedLocal(); if(local!=null&&JOptionPane.showConfirmDialog(this,"Eliminar esta copia local del historial. La partida actual se conserva.","Eliminar copia",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) work("Eliminando…",()-> { manager.deleteLocal(local); refreshLocalData(); });
    }
    private void deleteCloud() {
        var remote=selectedRemote(); if(remote!=null&&JOptionPane.showConfirmDialog(this,"Eliminar permanentemente esta versión de Google Drive.\n"+remote,"Eliminar de la nube",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION) work("Eliminando versión de Drive…",()-> { manager.deleteCloud(remote); refreshCloudData(); refreshLocalData(); });
    }
    private void background() {
        try {
            Path open=manager.config.home.resolve("open.request"); if(Files.deleteIfExists(open)) showApp();
            monitor.tick(); bridge.tick();
            long now=System.currentTimeMillis();
            if(manager.auth.connected()&&now-lastSync>60_000) { lastSync=now; manager.sync(); refreshLocalData(); }
            if(manager.auth.connected()&&now-lastCloud>180_000) refreshCloudData();
            lastAutoError="";
        } catch(Exception ex) { if(!Objects.equals(lastAutoError,ex.getMessage())) { log("Pendiente: "+ex.getMessage()); lastAutoError=ex.getMessage(); } }
    }
    static void openBrowser(URI uri) {
        try {
            if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) { Desktop.getDesktop().browse(uri); return; }
        } catch(IOException ignored) { /* Show the address if there is no registered browser. */ }
        SwingUtilities.invokeLater(()->JOptionPane.showInputDialog(null,"Abrí esta dirección en tu navegador:",uri.toString()));
    }
    private void closeChoice() {
        Object[] options={"Segundo plano","Salir","Cancelar"};
        int choice=JOptionPane.showOptionDialog(this,"El gestor debe seguir funcionando para respaldar al cerrar Zomboid y subir pendientes.","PZ Cloud Management",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,null,options,options[0]);
        if(choice==0) setVisible(false); else if(choice==1) { worker.shutdownNow(); dispose(); System.exit(0); }
    }
    private void installTray() {
        if(!SystemTray.isSupported()) return;
        try {
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_INT_ARGB); Graphics2D g=image.createGraphics();
            g.setColor(ACCENT); g.fillRoundRect(1,1,30,30,8,8); g.setColor(BG); g.setFont(new Font("SansSerif",Font.BOLD,20)); g.drawString("Z",9,24); g.dispose();
            PopupMenu menu=new PopupMenu(); MenuItem open=new MenuItem("Abrir PZ Cloud Management"); open.addActionListener(e->showApp()); menu.add(open); MenuItem exit=new MenuItem("Salir"); exit.addActionListener(e->SwingUtilities.invokeLater(this::closeChoice)); menu.add(exit);
            TrayIcon icon=new TrayIcon(image,"PZ Cloud Management",menu); icon.setImageAutoSize(true); icon.addActionListener(e->showApp()); SystemTray.getSystemTray().add(icon);
        } catch(AWTException ignored) { /* Reopening the application also shows an existing instance. */ }
    }
}
