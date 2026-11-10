package pzcloud;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Production loader check, with optional supplied publisher identity; never contacts Google. */
public final class BundledClientSmoke {
    public static void main(String[] args) throws Exception {
        Path home=Files.createTempDirectory("pzcloud-bundled-only-");
        String previous=System.getProperty("pzcloud.google.client");
        try {
            Path external=home.resolve("google-client.json"); FilesEx.json(external,Map.of("installed",Map.of("client_id","external.apps.googleusercontent.com","client_secret","external-public")));
            System.setProperty("pzcloud.google.client",external.toString());
            Map<String,Object> resource;
            try(InputStream in=GoogleClientDefaults.class.getResourceAsStream("/auth/google-client.json")) { resource=in==null?Map.of():Json.object(Json.parse(new String(in.readAllBytes(),StandardCharsets.UTF_8))); }
            if(!GoogleClientDefaults.load().equals(resource)) throw new AssertionError("An external client replaced the bundled resource");
            if(Arrays.asList(args).contains("--consumer")) {
                for(String type:List.of("pzcloud.PublisherConfig","pzcloud.GoogleSetupDialog")) { try { Class.forName(type); throw new AssertionError("Author tool shipped in the consumer JAR"); } catch(ClassNotFoundException expected) { /* Build-only tools must be absent. */ } }
            }
            String expectedFile=Arrays.stream(args).filter(a->!a.startsWith("--")).findFirst().orElse("");
            if(!expectedFile.isBlank()) {
                Map<String,Object> expected=Json.object(FilesEx.readJson(Path.of(expectedFile)).get("installed")),actual=Json.object(resource.get("installed"));
                for(String key:List.of("client_id","client_secret")) if(!Json.str(expected,key).equals(Json.str(actual,key))) throw new AssertionError("Supplied publisher identity was not bundled");
            }
            Config config=new Config(home); GoogleAuth auth=new GoogleAuth(home);
            if(resource.isEmpty()) {
                if(auth.configured()||auth.connected()||!config.get("cloudMode").equals("none")) throw new AssertionError("External files or environment enabled an unconfigured runtime");
            } else {
                String id=Json.str(Json.object(resource.get("installed")),"client_id");
                if(!auth.configured()||!config.get("cloudMode").equals("drive")||auth.connected()) throw new AssertionError("Fresh install did not use bundled Google");
                AtomicBoolean opened=new AtomicBoolean();
                try { auth.connect(uri->{ var q=GoogleAuth.query(uri.getRawQuery()); if(!uri.getHost().equals("accounts.google.com")||!id.equals(q.get("client_id"))||!"S256".equals(q.get("code_challenge_method"))||!GoogleAuth.SCOPE.equals(q.get("scope"))||!URI.create(q.get("redirect_uri")).getHost().equals("127.0.0.1")) throw new AssertionError("OAuth handoff used another identity"); opened.set(true); auth.cancelConnection(); }); throw new AssertionError("Cancelled authorization completed"); }
                catch(IOException ex) { if(!opened.get()||!ex.getMessage().contains("cancelado")) throw ex; }
            }
            if(Files.exists(home.resolve("google-session.token"))) throw new AssertionError("A personal account session was created");
            System.out.println("Bundled-only Google passed: external JSON/JVM/environment ignored, official handoff preserved, no personal account accessed.");
        } finally { if(previous==null) System.clearProperty("pzcloud.google.client"); else System.setProperty("pzcloud.google.client",previous); FilesEx.deleteTree(home); }
    }
}
