package pzcloud;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;

/** Native popup + local OAuth simulator; no Google account, browser process or credential picker. */
public final class SignInUiSmoke {
    private static <T extends Component> T find(Container root,Class<T> type,Predicate<T> predicate) {
        for(Component child:root.getComponents()) {
            if(type.isInstance(child)&&predicate.test(type.cast(child))) return type.cast(child);
            if(child instanceof Container container) { T nested=find(container,type,predicate); if(nested!=null) return nested; }
        }
        return null;
    }
    private static void visible(AtomicReference<? extends Window> ref) throws Exception {
        long until=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<until) {
            AtomicBoolean shown=new AtomicBoolean(); SwingUtilities.invokeAndWait(()->shown.set(ref.get()!=null&&ref.get().isShowing())); if(shown.get()) return; Thread.sleep(20);
        }
        throw new AssertionError("Sign-in popup did not open");
    }
    private static void image(Window dialog,String filename) throws Exception {
        SwingUtilities.invokeAndWait(()-> { try { var bitmap=new java.awt.image.BufferedImage(dialog.getWidth(),dialog.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB); var g=bitmap.createGraphics(); dialog.paint(g); g.dispose(); ImageIO.write(bitmap,"png",Path.of(filename).toFile()); } catch(Exception ex) { throw new RuntimeException(ex); } });
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("pzcloud-signin-ui-").toRealPath(); ExecutorService worker=Executors.newSingleThreadExecutor(); java.util.List<Window> dialogs=new ArrayList<>();
        try(SelfTest.MockDrive mock=new SelfTest.MockDrive()) {
            Path home=Files.createDirectories(root.resolve("configured")); GoogleAuth auth=new GoogleAuth(home,mock.base.resolve("token"),Duration.ofSeconds(20),()->SelfTest.desktopClient("publisher"));
            CountDownLatch browser=new CountDownLatch(1),connected=new CountDownLatch(1); AtomicReference<URI> url=new AtomicReference<>(); AtomicReference<SignInDialog> popup=new AtomicReference<>(); AtomicInteger openings=new AtomicInteger();
            SwingUtilities.invokeLater(()-> { SignInDialog dialog=new SignInDialog(null,auth,worker,connected::countDown,uri->{ openings.incrementAndGet(); url.set(uri); browser.countDown(); },()->auth.verifyDriveAccess(mock.base.resolve("drive-check"))); popup.set(dialog); dialogs.add(dialog); dialog.open(); });
            if(!browser.await(5,TimeUnit.SECONDS)) throw new AssertionError("No Google account chooser requested"); visible(popup);
            SwingUtilities.invokeAndWait(()-> { if(find(popup.get(),JPasswordField.class,p->true)!=null||find(popup.get(),JTextField.class,p->true)!=null) throw new AssertionError("App must not collect Google passwords/usernames"); });
            URI original=url.get();
            SwingUtilities.invokeAndWait(()-> { JButton button=find(popup.get(),JButton.class,b->b.getText().equals("Volver a abrir Google")); if(button==null||!button.isEnabled()) throw new AssertionError("Main Google button must remain usable while waiting"); button.doClick(); });
            if(openings.get()!=2||!original.equals(url.get())||!auth.isConnecting()) throw new AssertionError("Reopening Google must reuse the same authorization attempt");
            image(popup.get(),"build/ui-google-sign-in.png");
            var q=GoogleAuth.query(url.get().getRawQuery()); mock.challenge=q.get("code_challenge");
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(q.get("redirect_uri")+"?state="+q.get("state")+"&code=test-code")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(!connected.await(20,TimeUnit.SECONDS)) throw new AssertionError("Popup did not finish the OAuth connection");
            worker.submit(()->{}).get(5,TimeUnit.SECONDS); SwingUtilities.invokeAndWait(()-> { if(popup.get().isShowing()) throw new AssertionError("Successful popup should close automatically"); });
            if(!auth.connected()||!Files.exists(home.resolve("google-session.token"))||mock.driveChecks!=1) throw new AssertionError("Session was not remembered and checked against Drive");
            // Reuse does not open Google again or ask for a configuration file.
            CountDownLatch reused=new CountDownLatch(1); AtomicReference<SignInDialog> reuse=new AtomicReference<>();
            SwingUtilities.invokeLater(()-> { var dialog=new SignInDialog(null,auth,worker,reused::countDown,uri->openings.incrementAndGet(),()->auth.verifyDriveAccess(mock.base.resolve("drive-check"))); reuse.set(dialog); dialogs.add(dialog); dialog.open(); }); visible(reuse);
            SwingUtilities.invokeAndWait(()->find(reuse.get(),JButton.class,b->b.getText().equals("Usar esta conexión")).doClick());
            if(!reused.await(2,TimeUnit.SECONDS)||openings.get()!=2) throw new AssertionError("Existing session should be reusable without signing in again");
            if(mock.driveChecks!=2) throw new AssertionError("A reused session must also prove Drive access");
            mock.driveCheckStatus=403; AtomicBoolean falseSuccess=new AtomicBoolean(); AtomicReference<SignInDialog> refused=new AtomicReference<>();
            SwingUtilities.invokeLater(()-> { var dialog=new SignInDialog(null,auth,worker,()->falseSuccess.set(true),uri->openings.incrementAndGet(),()->auth.verifyDriveAccess(mock.base.resolve("drive-check"))); refused.set(dialog); dialogs.add(dialog); dialog.open(); }); visible(refused);
            SwingUtilities.invokeAndWait(()->find(refused.get(),JButton.class,b->b.getText().equals("Usar esta conexión")).doClick()); worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(()-> { if(falseSuccess.get()||!refused.get().isShowing()||find(refused.get(),JTextArea.class,t->t.getText().contains("habilitar Google Drive API"))==null) throw new AssertionError("A saved Google session without Drive access must not be presented as ready"); find(refused.get(),JButton.class,b->b.getText().equals("Cerrar")).doClick(); }); mock.driveCheckStatus=200;
            Path cancelHome=Files.createDirectories(root.resolve("cancel")); GoogleAuth cancelAuth=new GoogleAuth(cancelHome,mock.base.resolve("token"),Duration.ofSeconds(20),()->SelfTest.desktopClient("publisher"));
            CountDownLatch waiting=new CountDownLatch(1); AtomicReference<SignInDialog> cancelled=new AtomicReference<>();
            SwingUtilities.invokeLater(()-> { var dialog=new SignInDialog(null,cancelAuth,worker,()->falseSuccess.set(true),uri->waiting.countDown(),()->cancelAuth.verifyDriveAccess(mock.base.resolve("drive-check"))); cancelled.set(dialog); dialogs.add(dialog); dialog.open(); });
            if(!waiting.await(5,TimeUnit.SECONDS)) throw new AssertionError("Cancellation fixture did not start");
            SwingUtilities.invokeAndWait(()->find(cancelled.get(),JButton.class,b->b.getText().equals("Cancelar")).doClick()); worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            if(falseSuccess.get()||cancelAuth.connected()||Files.exists(cancelHome.resolve("google-session.token"))) throw new AssertionError("Cancelled popup must not claim a connected account");
            // The generic distribution is honest when the publisher has not provided an OAuth client.
            GoogleAuth missing=new GoogleAuth(Files.createDirectories(root.resolve("missing")),mock.base.resolve("token"),Duration.ofSeconds(20),Map::of); AtomicReference<SignInDialog> unconfigured=new AtomicReference<>(); AtomicReference<URI> helpUrl=new AtomicReference<>();
            SwingUtilities.invokeLater(()-> { var dialog=new SignInDialog(null,missing,worker,()->falseSuccess.set(true),helpUrl::set); unconfigured.set(dialog); dialogs.add(dialog); dialog.open(); }); visible(unconfigured);
            SwingUtilities.invokeAndWait(()-> { JButton button=find(unconfigured.get(),JButton.class,b->b.getText().equals("Iniciar sesión con Google")); if(!button.isEnabled()) throw new AssertionError("Google entry point must remain accessible without publisher configuration"); button.doClick(); if(find(unconfigured.get(),JTextArea.class,t->t.getText().contains("el autor debe habilitar Google"))==null) throw new AssertionError("Click must explain the missing publisher setup"); });
            if(helpUrl.get()!=null||missing.isConnecting()) throw new AssertionError("Missing configuration must not launch an invalid OAuth flow");
            image(unconfigured.get(),"build/ui-google-not-configured.png");
            SwingUtilities.invokeAndWait(()-> { if(find(unconfigured.get(),JButton.class,b->b.getText().equals("Habilitar Google (autor)"))==null) throw new AssertionError("Missing setup must expose the author assistant"); find(unconfigured.get(),JButton.class,b->b.getText().equals("Cerrar")).doClick(); });
            if(helpUrl.get()!=null||falseSuccess.get()||missing.connected()) throw new AssertionError("Unconfigured edition must not launch a fictitious connection");
            Path invalid=root.resolve("web.json"),client=root.resolve("desktop.json"); FilesEx.json(invalid,Map.of("web",Map.of("client_id","web.apps.googleusercontent.com"))); FilesEx.json(client,SelfTest.desktopClient("registered-fixture"));
            AtomicReference<Path> choice=new AtomicReference<>(invalid); AtomicReference<GoogleSetupDialog> setup=new AtomicReference<>(); CountDownLatch imported=new CountDownLatch(1);
            SwingUtilities.invokeLater(()-> { var dialog=new GoogleSetupDialog(null,missing,worker,imported::countDown,helpUrl::set,owner->choice.get()); setup.set(dialog); dialogs.add(dialog); dialog.setVisible(true); }); visible(setup);
            image(setup.get(),"build/ui-google-setup.png");
            SwingUtilities.invokeAndWait(()->find(setup.get(),JButton.class,b->b.getText().equals("Cargar JSON de la aplicación")).doClick()); worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(()-> { if(imported.getCount()!=1||find(setup.get(),JTextArea.class,t->t.getText().startsWith("No se pudo cargar:"))==null) throw new AssertionError("Setup must reject a web client and permit correction"); });
            choice.set(client); SwingUtilities.invokeAndWait(()->find(setup.get(),JButton.class,b->b.getText().equals("Cargar JSON de la aplicación")).doClick());
            if(!imported.await(5,TimeUnit.SECONDS)||!missing.configured()||missing.connected()) throw new AssertionError("Author setup must import only the application identity, not a user login");
            System.out.println("Sign-in UI passed: OAuth return, real Drive-permission probe via local simulator, remembered session, denied Drive access, cancellation and guided author setup");
        } finally {
            SwingUtilities.invokeAndWait(()->dialogs.forEach(Window::dispose)); worker.shutdownNow(); FilesEx.deleteTree(root);
        }
        System.exit(0);
    }
}
