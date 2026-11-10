package pzcloud;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
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
    private final JComboBox<String> source=new JComboBox<>(new String[]{I18n.t("En esta PC"),I18n.t("En mi carpeta / nube")});
    private final JLabel selectedName=new JLabel(I18n.t("Elegí una partida")),lastCopy=new JLabel(""),destination=new JLabel(""),status=new JLabel(I18n.t("Listo")),connection=new JLabel();
    private final JCheckBox automatic=new JCheckBox(I18n.t("Guardar automáticamente al cerrar Zomboid"));
    private final JTextField search=new JTextField(18);
    private final JLabel worldCount=new JLabel(),recoveryCount=new JLabel(I18n.t("Elegí dónde buscar tus copias.")),summary=new JLabel();
    private JButton saveButton,playButton,historyButton,restoreButton,optionsButton,syncButton,googleButton;
    private final JTextArea activity=new JTextArea(15,75);
    private final JProgressBar progress=new JProgressBar();
    private final JTabbedPane tabs=new JTabbedPane();
    private final List<JButton> buttons=new ArrayList<>();
    private List<SaveManager.Local> copies=List.of();
    private List<Config.World> allWorlds=List.of();
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
        super(Installation.NAME+" · "+Installation.VERSION+(manager.auth.configured()?I18n.t(" · Google incluido"):I18n.t(" · Solo copias locales"))); this.manager=manager; this.worker=worker;
        I18n.configure(manager.config); I18n.configureOptionPane();
        setTitle(Installation.NAME+" · "+Installation.VERSION+I18n.t(manager.auth.configured()?" · Google incluido":" · Solo copias locales"));
        theme(); service=new BackgroundService(manager,worker,this::showApp,()-> { try { loadLocal(); } catch(IOException ex) { log(ex.getMessage()); } },this::log);
        setMinimumSize(new Dimension(900,660)); setSize(1060,740); setLocationRelativeTo(null); setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel root=new JPanel(new BorderLayout(0,20)); root.setBorder(new EmptyBorder(25,28,18,28)); setContentPane(root);
        JPanel header=new JPanel(new BorderLayout(12,0)); JPanel titles=vertical();
        JLabel brand=new JLabel(Installation.DESCRIPTION+"  /  "+Installation.VERSION); brand.setForeground(GREEN); titles.add(brand); titles.add(Box.createVerticalStrut(7));
        JLabel title=new JLabel(Installation.NAME); title.setFont(new Font("Segoe UI",Font.BOLD,29)); titles.add(title); titles.add(Box.createVerticalStrut(7)); titles.add(muted(I18n.t("Tus partidas, a salvo. Seguí jugando donde quieras."))); header.add(titles,BorderLayout.WEST);
        JPanel settings=vertical(); connection.setForeground(GREEN); settings.add(connection); settings.add(Box.createVerticalStrut(12)); googleButton=button(I18n.t("Iniciar sesión con Google"),this::connectDrive); settings.add(row(googleButton,button(I18n.t("Configuración"),this::setup))); settings.add(row(button(I18n.t("Idioma / Language"),this::languageSettings))); header.add(settings,BorderLayout.EAST); root.add(header,BorderLayout.NORTH);
        tabs.addTab(I18n.t("Mis partidas"),home()); tabs.addTab(I18n.t("Recuperar partida"),recoverPanel()); root.add(tabs,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(12,0)); JPanel state=vertical(); state.add(status); progress.setIndeterminate(false); progress.setVisible(false); progress.setPreferredSize(new Dimension(200,5)); state.add(progress); footer.add(state,BorderLayout.CENTER);
        // This explicit action remains available even during a backup: hiding the UI must not cancel the work.
        JButton minimizeButton=new JButton(I18n.t("Minimizar a la bandeja"));
        minimizeButton.setMargin(new Insets(11,16,11,16)); minimizeButton.setFocusPainted(false);
        minimizeButton.setToolTipText(I18n.t("Knox Net sigue funcionando; abrilo desde el icono junto al reloj."));
        minimizeButton.addActionListener(e->minimizeToTray());
        JPanel footerActions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));
        footerActions.add(minimizeButton);
        footerActions.add(button(I18n.t("Ver actividad"),()->JOptionPane.showMessageDialog(this,new JScrollPane(activity),I18n.t("Actividad"),JOptionPane.PLAIN_MESSAGE)));
        footer.add(footerActions,BorderLayout.EAST); root.add(footer,BorderLayout.SOUTH);
        activity.setName("activity-log"); activity.setEditable(false); activity.setLineWrap(true); activity.setWrapStyleWord(true); activity.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        worlds.addListSelectionListener(e->{ if(!e.getValueIsAdjusting()&&!updating) { var w=worlds.getSelectedValue(); selectedKey=w==null?"":w.key(); showSelected(); } });
        recovery.addListSelectionListener(e->{ if(!e.getValueIsAdjusting()) updateActions(); });
        search.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e) { filterWorlds(); } public void removeUpdate(DocumentEvent e) { filterWorlds(); } public void changedUpdate(DocumentEvent e) { filterWorlds(); } });
        automatic.addActionListener(e->{ var w=worlds.getSelectedValue(); if(w==null||updating) return; boolean enabled=automatic.isSelected();
            work(I18n.t("Guardando preferencia…"),()-> { w.update(Map.of("automatic",enabled,"trigger","after_exit","destination",manager.config.get("cloudMode").equals("none")?"local":"both")); service.settingsChanged(); loadLocal(); return null; },v->{});
        });
        tabs.addChangeListener(e->{ if(started&&tabs.getSelectedIndex()==1) loadRecovery(); });
        addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { closeWindow(); } });
        SwingUtilities.updateComponentTreeUI(this); I18n.installSourceRenderer(source); I18n.localize(this); installTray(); updateConnection(); updateActions();
        work(I18n.t("Buscando tus partidas…"),()-> { if(!manager.guard.running()) manager.engine.recover(manager.config.saves()); service.start(); loadLocal(); return null; },v->{ started=true; if(!manager.config.get("setupComplete").equals("true")) { if(manager.auth.configured()) connectDrive(); else setup(); } });
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
    private static final class SaveCell extends JPanel implements ListCellRenderer<Object> {
        private static final long serialVersionUID=1L;
        private final JLabel name=new JLabel(),details=new JLabel();
        SaveCell() { setLayout(new BorderLayout(0,5)); setBorder(new EmptyBorder(10,14,10,14)); name.putClientProperty("html.disable",true); details.putClientProperty("html.disable",true); name.setFont(new Font("Segoe UI",Font.BOLD,15)); details.setFont(new Font("Segoe UI",Font.PLAIN,12)); add(name,BorderLayout.NORTH); add(details,BorderLayout.CENTER); }
        public Component getListCellRendererComponent(JList<?> list,Object item,int index,boolean selected,boolean focus) {
            setBackground(selected?new Color(56,83,61):CARD); name.setForeground(FG); details.setForeground(selected?FG:MUTED);
            if(item instanceof Config.World world) { name.setText(world.name); details.setText(world.mode+(world.automatic()?I18n.t(" · Guardado automático"):I18n.t(" · Guardado manual"))); }
            if(item instanceof Recovery copy) { name.setText(copy.name()); details.setText(friendlyDate(copy.date())+" · "+copy.device()+" · "+FilesEx.human(copy.bytes())+(copy.local()!=null?" · "+delivery(copy.local().status()):"")); }
            setToolTipText(name.getText()+" · "+details.getText()); return this;
        }
    }
    private JPanel home() {
        JPanel panel=new JPanel(new BorderLayout(20,10)); panel.setBorder(new EmptyBorder(20,0,0,0)); worlds.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); worlds.setFixedCellHeight(70); worlds.setCellRenderer(new SaveCell());
        JPanel sidebar=new JPanel(new BorderLayout(0,10)),filter=vertical(); filter.add(new JLabel(I18n.t("Buscar partida"))); filter.add(Box.createVerticalStrut(7)); search.setToolTipText(I18n.t("Filtra por nombre o modo sin volver a leer tus partidas.")); filter.add(search); filter.add(Box.createVerticalStrut(7)); worldCount.setForeground(MUTED); filter.add(worldCount); sidebar.add(filter,BorderLayout.NORTH);
        JScrollPane list=new JScrollPane(worlds); list.setPreferredSize(new Dimension(290,350)); sidebar.add(list,BorderLayout.CENTER); panel.add(sidebar,BorderLayout.WEST);
        JPanel detail=vertical(); detail.setBorder(new EmptyBorder(14,8,10,5)); selectedName.setFont(new Font("Segoe UI",Font.BOLD,24)); detail.add(selectedName); detail.add(Box.createVerticalStrut(15)); lastCopy.setForeground(MUTED); detail.add(lastCopy); detail.add(Box.createVerticalStrut(8)); detail.add(destination); detail.add(Box.createVerticalStrut(20));
        saveButton=primary(I18n.t("Guardar partida"),this::save); playButton=button(I18n.t("Jugar"),()->work(I18n.t("Preparando Zomboid…"),()-> { service.monitor().beforeLaunch(); openBrowser(URI.create("steam://rungameid/108600")); return null; },v->{})); syncButton=button(I18n.t("Enviar pendientes"),this::syncNow); detail.add(row(saveButton,playButton,syncButton));
        detail.add(Box.createVerticalStrut(16)); detail.add(automatic); detail.add(Box.createVerticalStrut(8)); detail.add(muted(I18n.t("La copia automática espera a que cierres el juego por completo."))); detail.add(Box.createVerticalStrut(16));
        historyButton=button(I18n.t("Versiones anteriores…"),()-> { source.setSelectedIndex(0); tabs.setSelectedIndex(1); loadRecovery(); }); detail.add(row(historyButton,button(I18n.t("Actualizar partidas"),()->work(I18n.t("Actualizando…"),()-> { loadLocal(); return null; },v->{}))));
        summary.setForeground(MUTED); detail.add(summary);
        detail.add(Box.createVerticalStrut(14)); detail.add(muted(I18n.t("Tu progreso actual se respalda antes de recuperar otra copia."))); JScrollPane details=new JScrollPane(detail); details.setBorder(null); details.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); panel.add(details,BorderLayout.CENTER); return panel;
    }
    private JPanel recoverPanel() {
        JPanel panel=new JPanel(new BorderLayout(0,14)); panel.setBorder(new EmptyBorder(20,0,0,0)); source.addActionListener(e->{ if(started&&tabs.getSelectedIndex()==1) loadRecovery(); });
        JPanel heading=vertical(); heading.add(row(new JLabel(I18n.t("Buscar copias:")),source,button(I18n.t("Actualizar lista"),this::loadRecovery))); recoveryCount.setForeground(MUTED); heading.add(recoveryCount); panel.add(heading,BorderLayout.NORTH); recovery.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); recovery.setFixedCellHeight(72); recovery.setCellRenderer(new SaveCell()); panel.add(new JScrollPane(recovery),BorderLayout.CENTER);
        restoreButton=primary(I18n.t("Recuperar esta partida"),()-> { Recovery item=recovery.getSelectedValue(); if(item==null) { message(I18n.t("Elegí la copia que querés recuperar.")); return; }
            if(item.local()!=null) restore(item.local()); else work(I18n.t("Trayendo la copia a esta PC…"),()->manager.download(item.remote()),this::restore);
        }); optionsButton=button(I18n.t("Más opciones…"),this::copyOptions); panel.add(row(restoreButton,button(I18n.t("Importar copia…"),this::importZip),optionsButton),BorderLayout.SOUTH); return panel;
    }
    private Config.World selected() { var w=worlds.getSelectedValue(); if(w==null) message(I18n.t("Primero elegí una partida. Si no aparece, revisá la carpeta del juego en Configuración.")); return w; }
    private void save() { var w=selected(); if(w==null) return;
        work(I18n.t("Guardando y verificando tu partida…"),()-> { w.set("destination",manager.config.get("cloudMode").equals("none")?"local":"both"); var local=manager.backup(w,"manual"); loadLocal(); service.changed(); return local; },local->{
            status.setText(manager.canSync()?I18n.t("Copia local lista. Se enviará cuando el juego esté cerrado."):manager.config.get("cloudMode").equals("drive")?I18n.t("Copia local lista. Envío pendiente: iniciá sesión con Google."):I18n.t("Copia guardada en esta PC."));
        });
    }
    private void syncNow() {
        if(!manager.canSync()) { connectDrive(); return; }
        work(I18n.t("Enviando tus copias pendientes…"),()-> { manager.guard.requireClosed(); manager.sync(); loadLocal(); service.changed(); return null; },v->status.setText(manager.pendingCount()==0?I18n.t("Tus copias terminaron de enviarse al destino elegido."):I18n.t("Quedan copias pendientes; se reintentará automáticamente.")));
    }
    private void loadLocal() throws IOException {
        List<Config.World> list=manager.worlds(); List<SaveManager.Local> history=manager.history(null);
        SwingUtilities.invokeLater(()-> { copies=history; allWorlds=list; filterWorlds(); updateConnection(); });
    }
    private void filterWorlds() {
        updating=true; worldModel.clear(); String query=search.getText().strip().toLowerCase(Locale.ROOT);
        for(var world:allWorlds) if((world.name+" "+world.mode).toLowerCase(Locale.ROOT).contains(query)) worldModel.addElement(world);
        Config.World selected=null; for(int i=0;i<worldModel.size();i++) if(worldModel.get(i).key().equals(selectedKey)) selected=worldModel.get(i);
        if(selected==null&&!worldModel.isEmpty()) selected=worldModel.firstElement();
        if(selected!=null) { worlds.setSelectedValue(selected,true); selectedKey=selected.key(); }
        worldCount.setText(query.isEmpty()?allWorlds.size()+I18n.t(" partidas"):worldModel.size()+I18n.t(" de ")+allWorlds.size()+I18n.t(" partidas")); updating=false; showSelected();
    }
    private void showSelected() {
        var w=worlds.getSelectedValue(); updating=true;
        if(w==null) { selectedName.setText(allWorlds.isEmpty()?I18n.t("Todavía no hay partidas"):I18n.t("Sin coincidencias")); lastCopy.setText(allWorlds.isEmpty()?I18n.t("Jugá una vez o recuperá una copia de otra PC."):I18n.t("Probá con otro nombre o borrá la búsqueda.")); automatic.setSelected(false); automatic.setEnabled(false); destination.setText(""); }
        else { selectedName.setText(w.name); var last=copies.stream().filter(c->c.snapshot().get("mode").equals(w.mode)&&c.snapshot().get("name").equals(w.name)).findFirst();
            lastCopy.setText(last.map(c->I18n.t("Última copia: ")+friendlyDate(c.snapshot().get("createdAt"))+" · "+delivery(c.status())).orElse(I18n.t("Todavía no hiciste una copia de esta partida.")));
            destination.setText(switch(manager.config.get("cloudMode")) { case "folder"->I18n.t("Destino: esta PC y tu carpeta sincronizada"); case "drive"->manager.auth.connected()?I18n.t("Destino: esta PC y Google Drive"):I18n.t("Destino: esta PC · conectá Google para enviar"); default->I18n.t("Destino: esta PC"); }); automatic.setSelected(w.automatic()&&!w.get("trigger").equals("before_enter")); automatic.setEnabled(!busy);
        } selectedName.setToolTipText(w==null?null:w.name); summary.setText(copies.size()+I18n.t(" copias guardadas · ")+manager.pendingCount()+I18n.t(" por enviar")); updating=false; updateActions();
    }
    private static String delivery(String state) { return switch(state) { case "synced"->I18n.t("en Google Drive"); case "folder"->I18n.t("en tu carpeta"); case "pending"->I18n.t("pendiente de enviar"); default->I18n.t("en esta PC"); }; }
    private void loadRecovery() {
        if(busy) return;
        boolean remote=source.getSelectedIndex()==1;
        if(remote&&!manager.canSync()) { recoveryModel.clear(); recoveryCount.setText(I18n.t("Conectá Google o elegí una carpeta sincronizada en Configuración.")); status.setText(I18n.t("La nube todavía no está conectada. Tus copias locales siguen disponibles.")); return; }
        work(remote?I18n.t("Buscando tus copias afuera…"):I18n.t("Leyendo tus copias…"),()-> {
            List<Recovery> result=new ArrayList<>(); if(remote) for(var r:manager.cloud.list()) result.add(new Recovery(null,r)); else for(var c:manager.history(null)) result.add(new Recovery(c,null)); return result;
        },items->{ recoveryModel.clear(); items.forEach(recoveryModel::addElement); recoveryCount.setText(items.isEmpty()?I18n.t("Todavía no hay copias en este lugar."):items.size()+I18n.t(" copias · Más recientes primero")); if(!items.isEmpty()) recovery.setSelectedIndex(0); updateActions(); });
    }
    private void restore(SaveManager.Local local) {
        String original=local.snapshot().get("name"); JTextField name=new JTextField(original+I18n.t(" (recuperada)"),28);
        JPanel p=vertical(); p.add(new JLabel(I18n.t("Recuperar «")+original+"»")); p.add(Box.createVerticalStrut(10)); p.add(new JLabel(I18n.t("Copia del ")+friendlyDate(local.snapshot().get("createdAt")))); p.add(Box.createVerticalStrut(16)); p.add(new JLabel(I18n.t("Nombre en esta PC:"))); p.add(name); p.add(Box.createVerticalStrut(12));
        p.add(muted(I18n.t("Usá un nombre nuevo para conservar ambas partidas."))); p.add(muted(I18n.t("Si el nombre ya existe, primero se respalda su progreso."))); p.add(muted(I18n.t("Necesitás la misma versión de PZ y los mismos mods.")));
        if(JOptionPane.showConfirmDialog(this,p,I18n.t("Recuperar partida"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
        String target=name.getText().trim(); work(I18n.t("Recuperando tu partida…"),()-> { var result=manager.restore(local,local.snapshot().get("mode"),target,true); loadLocal(); service.changed(); return result; },v->message(I18n.t("Listo. Abrí Zomboid y buscá «")+target+I18n.t("» en Cargar partida.")));
    }
    private void languageSettings() {
        String[] choices={"Auto / Automático","Español","English"};
        int selected=switch(I18n.preference()) { case "es"->1; case "en"->2; default->0; };
        Object choice=JOptionPane.showInputDialog(this,I18n.t("Elegí el idioma de Knox Net:"),
            I18n.t("Cambiar idioma"),JOptionPane.QUESTION_MESSAGE,null,choices,choices[selected]);
        if(choice==null) return;
        int index=Arrays.asList(choices).indexOf(choice);
        applyLanguage(index==1?"es":index==2?"en":"auto");
    }
    void applyLanguage(String selectedLanguage) {
        try {
            I18n.change(manager.config,selectedLanguage);
            I18n.configureOptionPane();
            setTitle(Installation.NAME+" · "+Installation.VERSION+I18n.t(manager.auth.configured()?" · Google incluido":" · Solo copias locales"));
            I18n.localize(this);
            filterWorlds(); updateConnection();
            if(recoveryModel.size()>0) recoveryCount.setText(recoveryModel.size()+I18n.t(" copias · Más recientes primero"));
            source.repaint(); worlds.repaint(); recovery.repaint();
            // Also update an open settings dialog without restarting background tasks.
            for(Window window:Window.getWindows()) if(window.isShowing()) I18n.localizeDialog(window);
            revalidate(); repaint();
        } catch(IOException ex) {
            JOptionPane.showMessageDialog(this,I18n.t("No se pudo guardar el idioma.")+" "+ex.getMessage(),
                Installation.NAME,JOptionPane.ERROR_MESSAGE);
        }
    }
    private void setup() {
        JTextField folder=new JTextField(manager.config.get("syncFolder"),36); folder.setEditable(false);
        JRadioButton local=new JRadioButton(I18n.t("Solo en esta PC")),synced=new JRadioButton(I18n.t("En una carpeta de Drive u OneDrive que ya uso")),direct=new JRadioButton(I18n.t("Google Drive directo"));
        ButtonGroup group=new ButtonGroup(); for(var r:List.of(local,synced,direct)) { group.add(r); r.setBackground(BG); r.setForeground(FG); }
        switch(manager.config.get("cloudMode")) { case "folder"->synced.setSelected(true); case "drive"->direct.setSelected(true); default->local.setSelected(true); }
        JButton choose=new JButton(I18n.t("Elegir carpeta…")); choose.addActionListener(e->{ Path p=chooseFolder(); if(p!=null) { String leaf=p.getFileName()==null?"":p.getFileName().toString(); folder.setText((leaf.equals("PZCloudManagement")?p:p.resolve("PZCloudManagement")).toString()); synced.setSelected(true); } });
        JPanel p=vertical();
        // Language is the first setting: use OS locale by default or save an explicit override.
        JLabel languageLabel=new JLabel(I18n.t("Idioma de la aplicación:"));
        JComboBox<String> languageChoice=new JComboBox<>(new String[]{"Auto / Automático","Español","English"});
        languageChoice.setSelectedIndex(switch(I18n.preference()) { case "es"->1; case "en"->2; default->0; });
        languageChoice.addActionListener(e->applyLanguage(switch(languageChoice.getSelectedIndex()) { case 1->"es"; case 2->"en"; default->"auto"; }));
        p.add(row(languageLabel,languageChoice));
        p.add(Box.createVerticalStrut(14));
        JLabel title=new JLabel(I18n.t("¿Dónde querés guardar tus copias?")); title.setFont(title.getFont().deriveFont(Font.BOLD,21)); p.add(title); p.add(Box.createVerticalStrut(15)); p.add(local); p.add(Box.createVerticalStrut(12)); p.add(synced); p.add(row(folder,choose)); p.add(muted(I18n.t("Elegí una carpeta que tu aplicación de nube ya sincronice."))); p.add(muted(I18n.t("Esperá a que esa aplicación confirme la subida antes de cambiar de PC."))); p.add(Box.createVerticalStrut(14)); p.add(direct); p.add(muted(I18n.t("Iniciá sesión en Google y autorizá tus copias. La conexión se recuerda en esta PC.")));
        p.add(row(dialogButton(I18n.t("Iniciar sesión con Google"),()-> { direct.setSelected(true); connectDrive(); }),dialogButton(I18n.t("Más opciones…"),this::advanced))); p.add(Box.createVerticalStrut(8)); p.add(muted(I18n.t("Al recuperar en otra PC, elegí allí la misma carpeta o cuenta.")));
        if(JOptionPane.showConfirmDialog(this,p,I18n.t("Configuración sencilla"),JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION) return;
        String mode=synced.isSelected()?"folder":direct.isSelected()?"drive":"none",path=folder.getText();
        if(mode.equals("folder")&&path.isBlank()) { message(I18n.t("Elegí la carpeta donde querés guardar las copias.")); return; }
        work(I18n.t("Preparando tus copias…"),()-> {
            if(mode.equals("folder")) { Path target=Path.of(path); FilesEx.separate(manager.config.home,target); FilesEx.separate(manager.config.saves(),target); Files.createDirectories(target); }
            manager.config.update(Map.of("cloudMode",mode,"syncFolder",path,"setupComplete",true));
            for(var w:manager.worlds()) if(w.automatic()) w.set("destination",mode.equals("none")?"local":"both");
            service.settingsChanged(); loadLocal(); return null;
        },v->{ if(mode.equals("drive")&&!manager.auth.connected()) status.setText(I18n.t("Conectá Google Drive desde Configuración para enviar tus copias.")); });
    }
    private void connectDrive() {
        if(busy) return;
        new SignInDialog(this,manager.auth,worker,()->work(I18n.t("Activando tu conexión…"),()-> {
            manager.config.update(Map.of("cloudMode","drive","setupComplete",true)); for(var world:manager.worlds()) if(world.automatic()) world.set("destination","both"); service.settingsChanged(); loadLocal(); return null;
        },v->status.setText(I18n.t("Google Drive comprobado. Ya podés guardar y recuperar tus partidas.")))).open();
    }
    private Path chooseFolder() { JFileChooser c=new JFileChooser(); c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); return c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION?c.getSelectedFile().toPath():null; }
    private void advanced() {
        JPanel p=vertical(); p.add(muted(I18n.t("Estas opciones no hacen falta para guardar y recuperar partidas.")));
        p.add(row(dialogButton(I18n.t("Cambiar carpeta Zomboid…"),()-> { Path target=chooseFolder(); if(target!=null) work(I18n.t("Cambiando carpeta…"),()-> { manager.guard.requireClosed(); FilesEx.separate(target.resolve("Saves"),manager.config.home); manager.config.set("zomboid",target.toString()); loadLocal(); return null; },v->message(I18n.t("Carpeta actualizada. Reiniciá el gestor para actualizar el botón dentro del juego."))); }),dialogButton(I18n.t("Instalar botón en el juego"),()->work(I18n.t("Instalando…"),()-> { service.bridge().installMod(); return null; },v->message(I18n.t("Activá Knox Net en el menú Mods de Zomboid."))))));
        p.add(row(dialogButton(I18n.t("Abrir mis copias"),()-> { try { Desktop.getDesktop().open(manager.config.archives().toFile()); } catch(IOException e) { message(e.getMessage()); } }),dialogButton(I18n.t("Ayuda de Google Drive"),()->openBrowser(URI.create("https://github.com/SantiagoPana/PZ-Cloud-Management/blob/development/docs/GOOGLE_DRIVE.md")))));
        p.add(row(dialogButton(I18n.t("Desconectar Google"),()-> { if(JOptionPane.showConfirmDialog(this,I18n.t("Las copias remotas se conservan. Las nuevas subidas irán a la próxima cuenta conectada."),I18n.t("Desconectar"),JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) work(I18n.t("Desconectando…"),()-> { manager.auth.disconnect(); service.settingsChanged(); return null; },v->updateConnection()); }),dialogButton(I18n.t("Cerrar el gestor por completo"),this::exit)));
        JOptionPane.showMessageDialog(this,p,I18n.t("Más opciones"),JOptionPane.PLAIN_MESSAGE);
    }
    private void importZip() { JFileChooser c=new JFileChooser(); c.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(I18n.t("Copia de Knox Net"),"zip")); if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) work(I18n.t("Verificando tu copia…"),()->manager.importArchive(c.getSelectedFile().toPath()),this::restore); }
    private void copyOptions() {
        Recovery item=recovery.getSelectedValue(); if(item==null) { message(I18n.t("Elegí una copia primero.")); return; }
        Object[] choices=item.local()!=null?new Object[]{I18n.t("Exportar ZIP"),I18n.t("Eliminar copia"),I18n.t("Cancelar")}:new Object[]{I18n.t("Eliminar copia remota"),I18n.t("Cancelar")};
        int choice=JOptionPane.showOptionDialog(this,I18n.t("La partida actual no se elimina con estas opciones."),I18n.t("Más opciones de copia"),JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,choices,choices[choices.length-1]);
        if(item.local()!=null&&choice==0) { JFileChooser c=new JFileChooser(); c.setSelectedFile(new File(item.name()+".zip")); if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return; Path target=c.getSelectedFile().toPath();
            if(Files.exists(target)&&JOptionPane.showConfirmDialog(this,I18n.t("El archivo existe. ¿Reemplazarlo?"),I18n.t("Exportar copia"),JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION) return;
            work(I18n.t("Exportando copia…"),()-> { FilesEx.separate(manager.config.archives(),target); manager.engine.verify(item.local().snapshot().archive(),item.local().snapshot().sha256()); Path temp=Files.createTempFile(target.toAbsolutePath().getParent(),".export-",".part"); try { Files.copy(item.local().snapshot().archive(),temp,StandardCopyOption.REPLACE_EXISTING); manager.engine.verify(temp,item.local().snapshot().sha256()); FilesEx.move(temp,target,true); } finally { Files.deleteIfExists(temp); } return null; },v->status.setText(I18n.t("Copia exportada.")));
        } else if(item.local()!=null&&choice==1||item.local()==null&&choice==0) {
            if(JOptionPane.showConfirmDialog(this,I18n.t("¿Eliminar definitivamente esta copia?"),I18n.t("Eliminar copia"),JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION) return;
            work(I18n.t("Eliminando copia…"),()-> { if(item.local()!=null) manager.deleteLocal(item.local()); else manager.deleteCloud(item.remote()); loadLocal(); return null; },v->loadRecovery());
        }
    }
    private <T> void work(String label,Task<T> action,Consumer<T> success) {
        if(busy) return; busy=true; buttons.forEach(b->b.setEnabled(false)); automatic.setEnabled(false); source.setEnabled(false); progress.setVisible(true); progress.setIndeterminate(true); log(label);
        worker.execute(()-> { T result=null; Exception error=null; try { result=action.run(); } catch(Exception ex) { error=ex; } T value=result; Exception failure=error;
            SwingUtilities.invokeLater(()-> { busy=false; buttons.forEach(b->b.setEnabled(true)); automatic.setEnabled(worlds.getSelectedValue()!=null); source.setEnabled(true); progress.setIndeterminate(false); progress.setVisible(false); updateConnection();
                if(failure!=null) { log(I18n.t("No se completó: ")+failure.getMessage()); JOptionPane.showMessageDialog(this,failure.getMessage(),I18n.t("No se completó la operación"),JOptionPane.ERROR_MESSAGE); }
                else { status.setText(I18n.t("Listo")); success.accept(value); } updateActions();
            });
        });
    }
    private void updateActions() { if(saveButton==null||restoreButton==null) return; boolean selected=worlds.getSelectedValue()!=null; saveButton.setEnabled(!busy&&selected); playButton.setEnabled(!busy&&selected); historyButton.setEnabled(!busy&&!copies.isEmpty()); restoreButton.setEnabled(!busy&&recovery.getSelectedValue()!=null); optionsButton.setEnabled(!busy&&recovery.getSelectedValue()!=null); syncButton.setEnabled(!busy&&manager.pendingCount()>0&&manager.canSync()); }
    private void updateConnection() { connection.setText(switch(manager.config.get("cloudMode")) { case "folder"->I18n.t("Carpeta sincronizada"); case "drive"->manager.auth.connected()?I18n.t("Google Drive conectado"):I18n.t("Google Drive sin conectar"); default->I18n.t("Copias en esta PC"); }); if(manager.pendingCount()>0) connection.setText(connection.getText()+" · "+manager.pendingCount()+I18n.t(" pendientes")); if(googleButton!=null) googleButton.setText(manager.auth.connected()?I18n.t("Mi conexión de Google"):I18n.t("Iniciar sesión con Google")); updateActions(); }
    public void log(String text) { SwingUtilities.invokeLater(()-> { status.setText(text); activity.append(LocalTime.now().withNano(0)+"  "+text+"\n"); if(activity.getDocument().getLength()>16000) activity.setText(activity.getText().substring(8000)); activity.setCaretPosition(activity.getDocument().getLength()); }); }
    private void message(String text) { JOptionPane.showMessageDialog(this,text,"Knox Net",JOptionPane.INFORMATION_MESSAGE); }
    public void showApp() { SwingUtilities.invokeLater(()-> { setVisible(true); setState(Frame.NORMAL); toFront(); }); }
    static void openBrowser(URI uri) { try { if(Desktop.isDesktopSupported()&&Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) { Desktop.getDesktop().browse(uri); return; } } catch(IOException ignored) {} SwingUtilities.invokeLater(()->JOptionPane.showInputDialog(null,I18n.t("Abrí esta dirección en tu navegador:"),uri.toString())); }
    private static String friendlyDate(String text) { try { return DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(text)); } catch(Exception e) { return text; } }
    private void minimizeToTray() {
        if(tray!=null) {
            setVisible(false); // Keep the live window and process: do NOT dispose the frame.
            tray.displayMessage("Knox Net",I18n.t("Sigue abierto en la bandeja. Hacé clic en el icono para volver, o elegí Salir para cerrarlo."),TrayIcon.MessageType.INFO);
        } else {
            // On systems without a tray icon, never leave an inaccessible hidden process.
            setState(Frame.ICONIFIED);
        }
    }
    boolean trayAvailable() { return tray!=null; } // For isolated native UI tests.
    private void closeWindow() {
        if(busy) { message(I18n.t("Esperá a que termine la operación antes de cerrar.")); return; }
        if(manager.config.automaticExits()||(manager.hasPending()&&manager.canSync())) minimizeToTray();
        else exit();
    }
    private void exit() { if(busy) { message(I18n.t("Esperá a que termine la operación antes de salir.")); return; } close(); System.exit(0); }
    public void close() { try { service.close(); } catch(IOException ignored) {} worker.shutdownNow(); if(tray!=null) SystemTray.getSystemTray().remove(tray); dispose(); }
    private void installTray() {
        if(!SystemTray.isSupported()) return;
        try { var image=new java.awt.image.BufferedImage(24,24,java.awt.image.BufferedImage.TYPE_INT_ARGB); var g=image.createGraphics(); g.setColor(GREEN); g.fillRoundRect(0,0,24,24,6,6); g.setColor(BG); g.setFont(new Font("SansSerif",Font.BOLD,18)); g.drawString("Z",6,19); g.dispose(); PopupMenu menu=new PopupMenu(); MenuItem show=new MenuItem(I18n.t("Abrir Knox Net")); show.addActionListener(e->showApp()); menu.add(show); MenuItem quit=new MenuItem(I18n.t("Salir")); quit.addActionListener(e->SwingUtilities.invokeLater(this::exit)); menu.add(quit); tray=new TrayIcon(image,Installation.NAME+" "+Installation.VERSION,menu); tray.setImageAutoSize(true); tray.addActionListener(e->showApp()); SystemTray.getSystemTray().add(tray); } catch(AWTException ignored) {}
    }
}
