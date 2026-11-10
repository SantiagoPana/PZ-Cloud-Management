package pzcloud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Application identity supplied by the publisher, never an end-user sign-in token. */
final class GoogleClientDefaults {
    private GoogleClientDefaults() {}
    static Map<String,Object> load() throws IOException {
        try(InputStream resource=GoogleClientDefaults.class.getResourceAsStream("/auth/google-client.json")) {
            if(resource!=null) {
                byte[] bytes=resource.readNBytes(65537);
                if(bytes.length>65536) throw new IOException("La configuración de Google del instalable es inválida.");
                return Json.object(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
            }
        }
        return Map.of();
    }
}
