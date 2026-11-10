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
    private static void visible(AtomicReference<SignInDialog> ref) throws Exception {
        long until=System.nanoTime()+5_000_000_000L;
        while(System.nanoTime()<until) {
            AtomicBoolean shown=new AtomicBoolean(); SwingUtilities.invokeAndWait(()->shown.set(ref.get()!=null&&ref.get().isShowing())); if(shown.get()) return; Thread.sleep(20);
        }
        throw new AssertionError("Sign-in popup did not open");
    }
    private static void image(SignInDialog dialog,String filename) throws Exception {
        SwingUtilities.invokeAndWait(()-> { try { var bitmap=new java.awt.image.BufferedImage(dialog.getWidth(),dialog.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB); var g=bitmap.createGraphics(); dialog.paint(g); g.dispose(); ImageIO.write(bitmap,"png",Path.of(filename).toFile()); } catch(Exception ex) { throw new RuntimeException(ex); } });
    }
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("pzcloud-signin-ui-").toRealPath(); ExecutorService worker=Executors.newSingleThreadExecutor(); java.util.List<SignInDialog> dialogs=new ArrayList<>();
        try(SelfTest.MockDrive mock=new SelfTest.MockDrive()) {
            Path home=Files.createDirectories(root.resolve("configured")); GoogleAuth auth=new GoogleAuth(home,mock.base.resolve("token"),Duration.ofSeconds(20),()->SelfTest.desktopClient("publisher"));
            CountDownLatch browser=new CountDownLatch(1),connected=new CountDownLatch(1); AtomicReference<URI> url=new AtomicReference<>(); AtomicReference<SignInDialog> popup=new AtomicReference<>(); AtomicInteger openings=new AtomicInteger();
            SwingUtilities.invokeLater(()-> { SignInDialog dialog=new SignInDialog(null,auth,worker,connected::countDown,uri->{ openings.incrementAndGet(); url.set(uri); browser.countDown(); }); popup.set(dialog); dialogs.add(dialog); dialog.open(); });
            if(!browser.await(5,TimeUnit.SECONDS)) throw new AssertionError("No Google account chooser requested"); visible(popup);
            SwingUtilities.invokeAndWait(()-> { if(find(popup.get(),JPasswordField.class,p->true)!=null||find(popup.get(),JTextField.class,p->true)!=null) throw new AssertionError("App must not collect Google passwords/usernames"); });
            image(popup.get(),"build/ui-google-sign-in.png");
            var q=GoogleAuth.query(url.get().getRawQuery()); mock.challenge=q.get("code_challenge");
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(q.get("redirect_uri")+"?state="+q.get("state")+"&code=test-code")).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(!connected.await(20,TimeUnit.SECONDS)) throw new AssertionError("Popup did not finish the OAuth connection");
            worker.submit(()->{}).get(5,TimeUnit.SECONDS); SwingUtilities.invokeAndWait(()-> { if(popup.get().isShowing()) throw new AssertionError("Successful popup should close automatically"); });
            if(!auth.connected()||!Files.exists(home.resolve("google-session.token"))) throw new AssertionError("Session was not remembered");
            // Reuse does not open Google again or ask for a configuration file.
            CountDownLatch reused=new CountDownLatch(1); AtomicReference<SignInDialog> reuse=new AtomicReference<>();
            SwingUtilities.invokeLater(()-> { var dialog=new SignInDialog(null,auth,worker,reused::countDown,uri->openings.incrementAndGet()); reuse.set(dialog); dialogs.add(dialog); dialog.open(); }); visible(reuse);
            SwingUtilities.invokeAndWait(()->find(reuse.get(),JButton.class,b->b.getText().equals("Usar esta conexión")).doClick());
            if(!reused.await(2,TimeUnit.SECONDS)||openings.get()!=1) throw new AssertionError("Existing session should be reusable without signing in again");
            Path cancelHome=Files.createDirectories(root.resolve("cancel")); GoogleAuth cancelAuth=new GoogleAuth(cancelHome,mock.base.resolve("token"),Duration.ofSeconds(20),()->SelfTest.desktopClient("publisher"));
            CountDownLatch waiting=new CountDownLatch(1); AtomicReference<SignInDialog> cancelled=new AtomicReference<>(); AtomicBoolean falseSuccess=new AtomicBoolean();
            SwingUtilities.invokeLater(()-> { var dialog=new SignInDialog(null,cancelAuth,worker,()->falseSuccess.set(true),uri->waiting.countDown()); cancelled.set(dialog); dialogs.add(dialog); dialog.open(); });
            if(!waiting.await(5,TimeUnit.SECONDS)) throw new AssertionError("Cancellation fixture did not start");
            SwingUtilities.invokeAndWait(()->find(cancelled.get(),JButton.class,b->b.getText().equals("Cancelar")).doClick()); worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            if(falseSuccess.get()||cancelAuth.connected()||Files.exists(cancelHome.resolve("google-session.token"))) throw new AssertionError("Cancelled popup must not claim a connected account");
            // The generic distribution is honest when the publisher has not provided an OAuth client.
            GoogleAuth missing=new GoogleAuth(Files.createDirectories(root.resolve("missing")),mock.base.resolve("token"),Duration.ofSeconds(20),Map::of); AtomicReference<SignInDialog> unconfigured=new AtomicReference<>(); AtomicInteger forbiddenBrowser=new AtomicInteger();
            SwingUtilities.invokeLater(()-> { var dialog=new SignInDialog(null,missing,worker,()->falseSuccess.set(true),uri->forbiddenBrowser.incrementAndGet()); unconfigured.set(dialog); dialogs.add(dialog); dialog.open(); }); visible(unconfigured);
            image(unconfigured.get(),"build/ui-google-not-configured.png");
            SwingUtilities.invokeAndWait(()-> { if(find(unconfigured.get(),JButton.class,b->b.getText().equals("Iniciar sesión con Google")).isEnabled()) throw new AssertionError("Missing client must not fake a usable Google login"); find(unconfigured.get(),JButton.class,b->b.getText().equals("Cerrar")).doClick(); });
            if(forbiddenBrowser.get()!=0||falseSuccess.get()) throw new AssertionError("Unconfigured app attempted sign-in");
            System.out.println("Sign-in UI passed: browser handoff, automatic return, remembered session, cancellation, no end-user JSON and honest publisher-setup state");
        } finally {
            SwingUtilities.invokeAndWait(()->dialogs.forEach(Window::dispose)); worker.shutdownNow(); FilesEx.deleteTree(root);
        }
        System.exit(0);
    }
}
