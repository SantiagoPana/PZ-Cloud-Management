package pzcloud;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Build-time publisher utility. It never accepts a user's access/refresh token. */
public final class PublisherConfig {
    private PublisherConfig() {}
    static boolean prepare(Path resources,Path report,Path input,boolean required) throws IOException {
        Map<String,Object> document;
        if(input!=null) document=GoogleClientDefaults.read(input);
        else {
            String json=System.getenv("PZCLOUD_GOOGLE_CLIENT_JSON");
            document=json!=null&&!json.isBlank()?Json.object(Json.parse(json)):GoogleClientDefaults.load();
        }
        return prepareDocument(resources,report,document,required);
    }
    static boolean prepareDocument(Path resources,Path report,Map<String,Object> document,boolean required) throws IOException {
        FilesEx.json(report,Map.of("version","2.2.0","googleClientBundled",false,"liveGoogleLoginTested",false));
        Path target=resources.resolve("auth/google-client.json"); boolean configured=!document.isEmpty();
        // A new build cannot accidentally retain the identity from an older configured build.
        Files.deleteIfExists(target);
        if(configured) {
            Map<String,Object> installed=Json.object(document.get("installed")); String id=Json.str(installed,"client_id");
            if(Json.str(installed,"client_secret").length()>4096) throw new IOException("El cliente de Google del autor tiene un formato inválido.");
            if(id.length()>256||!id.endsWith(".apps.googleusercontent.com")) throw new IOException("El autor debe usar un cliente de Google de tipo Aplicación de escritorio.");
            FilesEx.json(target,Map.of("installed",Map.of("client_id",id,"client_secret",Json.str(installed,"client_secret"))));
        }
        FilesEx.json(report,Map.of("version","2.2.0","googleClientBundled",configured,"liveGoogleLoginTested",false));
        if(required&&!configured) throw new IOException("Falta el cliente de Google de la app. Esta compilación exige publicarlo configurado.");
        return configured;
    }
    public static void main(String[] args) throws Exception {
        if(args.length<2) throw new IOException("Uso: PublisherConfig <resources> <report> [client-file] [--required]");
        Path input=null; boolean required=false;
        for(int i=2;i<args.length;i++) if(args[i].equals("--required")) required=true; else input=Path.of(args[i]);
        boolean configured=prepare(Path.of(args[0]),Path.of(args[1]),input,required);
        System.out.println(configured?"Google: identidad de aplicación incluida; falta validar el ingreso con una cuenta real.":"Google: pendiente de habilitar por el autor; no se incluyeron credenciales de ejemplo.");
    }
}
