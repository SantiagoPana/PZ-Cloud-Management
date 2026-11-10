package pzcloud;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small native sign-in window. The password is entered only in Google's system-browser page. */
public final class SignInDialog extends JDialog {
    private static final long serialVersionUID=1L;
    @FunctionalInterface interface Browser { void open(URI address) throws Exception; }
    private final GoogleAuth auth;
    private final ExecutorService worker;
    private final Runnable connected;
    private final Browser browser;
    private final JTextArea description=text(3),status=text(3);
    private final JButton login=new JButton("Iniciar sesión con Google"),cancel=new JButton("Cancelar"),reopen=new JButton("Volver a abrir Google"),copy=new JButton("Copiar enlace"),change=new JButton("Elegir otra cuenta");
    private final JPanel links=new JPanel(new FlowLayout(FlowLayout.CENTER,8,4));
    private final AtomicBoolean started=new AtomicBoolean(),finishing=new AtomicBoolean();
    private Future<?> task;
    private URI address;
    private boolean running,dismissed;
    public SignInDialog(Window owner,GoogleAuth auth,ExecutorService worker,Runnable connected) {
        this(owner,auth,worker,connected,uri->{
            if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new IOException("No hay un navegador predeterminado.");
            Desktop.getDesktop().browse(uri);
        });
    }
    SignInDialog(Window owner,GoogleAuth auth,ExecutorService worker,Runnable connected,Browser browser) {
        super(owner,"Conectar Google Drive",ModalityType.APPLICATION_MODAL); this.auth=auth; this.worker=worker; this.connected=connected; this.browser=browser;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setResizable(false);
        JPanel root=new JPanel(); root.setLayout(new BoxLayout(root,BoxLayout.Y_AXIS)); root.setBorder(new EmptyBorder(24,26,18,26)); setContentPane(root);
        JLabel title=new JLabel("Conectá tu cuenta de Google"); title.setFont(new Font("Segoe UI",Font.BOLD,22)); title.setAlignmentX(Component.CENTER_ALIGNMENT); root.add(title); root.add(Box.createVerticalStrut(14));
        description.setText("Elegí tu cuenta en Google y autorizá el acceso a tus copias. Al terminar, volvés automáticamente a PZ Cloud."); root.add(description); root.add(Box.createVerticalStrut(14));
        login.setFont(new Font("Segoe UI",Font.BOLD,15)); login.setBackground(Color.WHITE); login.setForeground(new Color(38,46,48)); login.setMargin(new Insets(12,18,12,18)); login.setFocusPainted(false); login.setAlignmentX(Component.CENTER_ALIGNMENT); root.add(login); root.add(Box.createVerticalStrut(14));
        root.add(status); links.add(reopen); links.add(copy); links.setVisible(false); root.add(links);
        JPanel bottom=new JPanel(new FlowLayout(FlowLayout.CENTER,10,0)); bottom.add(change); bottom.add(cancel); root.add(Box.createVerticalStrut(8)); root.add(bottom);
        for(JButton button:new JButton[]{cancel,reopen,copy,change}) button.setFocusPainted(false);
        login.addActionListener(e->{ if(auth.connected()&&!running) finish(); else begin(); });
        change.addActionListener(e->begin()); cancel.addActionListener(e->cancel());
        reopen.addActionListener(e->openBrowser()); copy.addActionListener(e->{ if(address!=null) { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(address.toString()),null); status.setText("Enlace copiado. Abrilo en tu navegador y elegí tu cuenta."); } });
        addWindowListener(new WindowAdapter() { public void windowClosing(WindowEvent e) { cancel(); } });
        ready(); pack(); setLocationRelativeTo(owner);
    }
    private static JTextArea text(int rows) {
        JTextArea text=new JTextArea(rows,43); text.setEditable(false); text.setFocusable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setBorder(null); text.setBackground(UIManager.getColor("Panel.background")); text.setForeground(UIManager.getColor("Label.foreground")); text.setFont(new Font("Segoe UI",Font.PLAIN,14)); return text;
    }
    private void ready() {
        boolean configured=auth.configured(),existing=auth.connected(); login.setEnabled(configured||existing); change.setVisible(existing&&configured); cancel.setEnabled(true); cancel.setText(existing?"Cerrar":"Cancelar");
        login.setText(existing?"Usar esta conexión":"Iniciar sesión con Google");
        if(existing) status.setText("Tu conexión de Google ya está guardada en esta PC.");
        else if(!configured) { description.setText(auth.configurationProblem()); status.setText("Falta habilitar Google para esta aplicación. No necesitás buscar un archivo ni configurar una cuenta de desarrollador."); cancel.setText("Cerrar"); }
        else status.setText(auth.sessionProblem().isBlank()?"La sesión se guarda para que no tengas que repetir este paso.":auth.sessionProblem());
    }
    /** One click from the main app opens this window and launches the official account chooser. */
    public void open() {
        if(auth.configured()&&!auth.connected()) SwingUtilities.invokeLater(this::begin);
        setVisible(true);
    }
    private void begin() {
        if(running||dismissed||!auth.configured()) return;
        running=true; started.set(false); finishing.set(false); login.setEnabled(false); change.setEnabled(false); cancel.setText("Cancelar"); cancel.setEnabled(true); status.setText("Preparando el inicio de sesión…");
        task=worker.submit(()-> {
            started.set(true);
            try {
                auth.connect(uri->SwingUtilities.invokeLater(()-> {
                    if(dismissed) return; address=uri; links.setVisible(true); pack(); status.setText("Terminá de iniciar sesión en la pantalla de Google. Esta ventana se cierra al conectar."); openBrowser();
                }),phase->{
                    if(phase.equals("committing")) finishing.set(true);
                    SwingUtilities.invokeLater(()-> {
                        if(dismissed) return;
                        if(phase.equals("exchanging")) status.setText("Google respondió. Terminando la conexión…");
                        if(phase.equals("committing")) { status.setText("Guardando tu conexión en esta PC…"); cancel.setEnabled(false); }
                    });
                });
                finishing.set(true); SwingUtilities.invokeLater(()-> { if(!dismissed) { running=false; finish(); } });
            } catch(Exception ex) {
                SwingUtilities.invokeLater(()-> { if(dismissed) return; running=false; finishing.set(false); address=null; links.setVisible(false); ready(); status.setText(ex.getMessage()==null?"No se pudo conectar. Intentá otra vez.":ex.getMessage()); pack(); });
            }
        });
    }
    private void openBrowser() { if(address==null) return; try { browser.open(address); } catch(Exception ex) { status.setText("No se pudo abrir el navegador. Podés copiar el enlace y abrirlo manualmente para continuar."); } }
    private void finish() { dismissed=true; dispose(); connected.run(); }
    private void cancel() {
        if(!running) { dismissed=true; dispose(); return; }
        if(!started.get()&&task!=null&&task.cancel(false)) { dismissed=true; dispose(); return; }
        if(auth.cancelConnection()) { dismissed=true; if(task!=null) task.cancel(false); dispose(); return; }
        if(auth.isConnecting()||finishing.get()) { cancel.setEnabled(false); status.setText("La conexión ya se está guardando. Falta un instante…"); return; }
        dismissed=true; if(task!=null) task.cancel(true); dispose();
    }
}
