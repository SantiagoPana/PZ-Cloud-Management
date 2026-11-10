package pzcloud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Application identity supplied by the publisher, never an end-user sign-in token. */
final class GoogleClientDefaults {
    private GoogleClientDefaults() {}
    static Map<String,Object> load() throws IOException {
        String override=System.getProperty("pzcloud.google.client","");
        if(!override.isBlank()) return read(Path.of(override));
        try(InputStream resource=GoogleClientDefaults.class.getResourceAsStream("/auth/google-client.json")) {
            if(resource!=null) {
                byte[] bytes=resource.readNBytes(65537);
                if(bytes.length>65536) throw new IOException("La configuración de Google del instalable es inválida.");
                return Json.object(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
            }
        }
        String id=System.getenv("PZCLOUD_GOOGLE_CLIENT_ID");
        if(id!=null&&!id.isBlank()) return Map.of("installed",Map.of("client_id",id.trim(),"client_secret",System.getenv().getOrDefault("PZCLOUD_GOOGLE_CLIENT_SECRET","")));
        return Map.of();
    }
    static Map<String,Object> read(Path file) throws IOException {
        if(Files.size(file)>65536) throw new IOException("El archivo de conexión de la app es demasiado grande.");
        return FilesEx.readJson(file);
    }
}
