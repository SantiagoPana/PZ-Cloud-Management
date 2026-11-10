package pzcloud;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/** One-time publisher setup. Configured distributions skip this window entirely. */
final class GoogleSetupDialog extends JDialog {
    private static final long serialVersionUID=1L;
    @FunctionalInterface interface ClientChooser { Path choose(Component owner); }
    private final GoogleAuth auth;
    private final ExecutorService worker;
    private final Runnable installed;
    private final SignInDialog.Browser browser;
    private final ClientChooser chooser;
    private final JTextArea state=new JTextArea(3,48);
    private final JButton load=new JButton("Cargar JSON de la aplicación"),close=new JButton("Cerrar");
    private boolean busy;
    GoogleSetupDialog(Window owner,GoogleAuth auth,ExecutorService worker,Runnable installed,SignInDialog.Browser browser) {
        this(owner,auth,worker,installed,browser,parent->{
            JFileChooser picker=new JFileChooser(); picker.setDialogTitle("Cliente OAuth de PZ Cloud: Aplicación de escritorio");
            picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Cliente OAuth de escritorio","json"));
            return picker.showOpenDialog(parent)==JFileChooser.APPROVE_OPTION?picker.getSelectedFile().toPath():null;
        });
    }
    GoogleSetupDialog(Window owner,GoogleAuth auth,ExecutorService worker,Runnable installed,SignInDialog.Browser browser,ClientChooser chooser) {
        super(owner,"Habilitar Google para PZ Cloud",ModalityType.APPLICATION_MODAL); this.auth=auth; this.worker=worker; this.installed=installed; this.browser=browser; this.chooser=chooser;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setResizable(false);
        JPanel root=new JPanel(); root.setLayout(new BoxLayout(root,BoxLayout.Y_AXIS)); root.setBorder(new EmptyBorder(22,24,20,24)); setContentPane(root);
        JLabel title=new JLabel("Configuración única del autor"); title.setFont(new Font("Segoe UI",Font.BOLD,22)); root.add(title); root.add(Box.createVerticalStrut(12));
        JTextArea steps=new JTextArea("1. Creá o elegí el proyecto PZ Cloud en Google Cloud.\n2. Habilitá Google Drive API y configurá el consentimiento.\n3. Creá un cliente de tipo Aplicación de escritorio y descargá su JSON.\n4. Cargalo acá para iniciar sesión y comprobar el acceso a Drive.",5,48); style(steps); root.add(steps);
        JTextArea note=new JTextArea("Este paso identifica al programa ante Google. Para distribuirlo, incluí el mismo cliente en la compilación: tus usuarios solo iniciarán sesión. Si el proyecto está en pruebas, agregá tu cuenta como usuario de prueba.",4,48); style(note); root.add(Box.createVerticalStrut(10)); root.add(note);
        JPanel links=new JPanel(new FlowLayout(FlowLayout.LEFT,8,6)); JButton console=new JButton("Abrir Google Cloud"),guide=new JButton("Ver guía completa"); links.add(console); links.add(guide); root.add(links);
        console.addActionListener(e->open("https://console.cloud.google.com/auth/clients")); guide.addActionListener(e->open("https://github.com/SantiagoPana/PZ-Cloud-Management/blob/development/docs/AUTHOR_GOOGLE.md"));
        style(state); state.setText("Elegí el JSON de la aplicación registrado en Google. Los tokens personales no se usan como configuración del programa."); root.add(state);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0)); actions.add(load); actions.add(close); root.add(actions);
        load.addActionListener(e->load()); close.addActionListener(e->{ if(!busy) dispose(); }); addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { if(!busy) dispose(); } });
        for(Component child:root.getComponents()) if(child instanceof JComponent component) component.setAlignmentX(Component.LEFT_ALIGNMENT);
        pack(); setLocationRelativeTo(owner);
    }
    private static void style(JTextArea text) { text.setEditable(false); text.setFocusable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setFont(new Font("Segoe UI",Font.PLAIN,14)); text.setBackground(UIManager.getColor("Panel.background")); text.setForeground(UIManager.getColor("Label.foreground")); text.setBorder(null); }
    private void open(String address) { try { browser.open(URI.create(address)); } catch(Exception ex) { state.setText("No se pudo abrir el navegador. La guía también está en docs/AUTHOR_GOOGLE.md dentro del programa."); } }
    private void load() {
        if(busy) return; Path file=chooser.choose(this); if(file==null) return;
        busy=true; load.setEnabled(false); close.setEnabled(false); state.setText("Comprobando la identidad de la aplicación…");
        worker.execute(()-> { Exception failure=null; try { auth.importClient(file); } catch(Exception ex) { failure=ex; } Exception result=failure;
            SwingUtilities.invokeLater(()-> { busy=false; load.setEnabled(true); close.setEnabled(true); if(result!=null) { state.setText("No se pudo cargar: "+result.getMessage()); return; } dispose(); installed.run(); });
        });
    }
}
