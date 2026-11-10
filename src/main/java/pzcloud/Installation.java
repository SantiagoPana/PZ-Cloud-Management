package pzcloud;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Identifies the code actually running, without exposing Google client IDs or secrets. */
final class Installation {
    static final String NAME="Knox Net";
    static final String DESCRIPTION="PZ Cloud Management";
    static final String VERSION="2.3.4";
    private Installation() {}
    static Path location() throws IOException {
        try { return Path.of(Installation.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize(); }
        catch(Exception ex) { throw new IOException("No se pudo identificar la instalación de Knox Net.",ex); }
    }
    static String fingerprint() throws IOException {
        Path source=location();
        return Files.isRegularFile(source)?FilesEx.hash(source,"SHA-256"):"development:"+source+":"+VERSION;
    }
    static Map<String,Object> report(GoogleAuth auth) throws IOException {
        Map<String,Object> report=new LinkedHashMap<>(); report.put("applicationName",NAME); report.put("description",DESCRIPTION); report.put("version",VERSION); report.put("googleIncluded",auth.configured()); report.put("googleStatus",auth.configurationDiagnostic()); report.put("application",location().toString()); report.put("build",fingerprint()); return report;
    }
    static String describe(GoogleAuth auth) {
        try { var info=report(auth); return NAME+" "+VERSION+"\n"+DESCRIPTION+"\nGoogle: "+(auth.configured()?"incluido":"no incluido")+"\nEstado: "+info.get("googleStatus")+"\nArchivo: "+info.get("application")+"\nCompilación: "+info.get("build"); }
        catch(IOException ex) { return NAME+" "+VERSION+"\n"+ex.getMessage(); }
    }
    static void writeDiagnostic(Path output) throws IOException {
        Path temp=Files.createTempDirectory("pzcloud-install-check-");
        try { Config config=new Config(temp); GoogleAuth auth=new GoogleAuth(temp); var data=report(auth); data.put("freshCloudMode",config.get("cloudMode")); data.put("personalSessionCreated",auth.connected()); FilesEx.json(output,data); }
        finally { FilesEx.deleteTree(temp); }
    }
}
