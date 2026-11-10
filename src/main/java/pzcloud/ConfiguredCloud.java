package pzcloud;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Providers are created on demand: local/folder mode starts no HTTP client or selector thread. */
final class ConfiguredCloud implements CloudProvider {
    private final Config config;
    private final GoogleAuth auth;
    private final Consumer<String> progress;
    private final GameGuard guard;
    private CloudProvider current;
    private String key="";
    ConfiguredCloud(Config config,GoogleAuth auth,Consumer<String> progress,GameGuard guard) { this.config=config; this.auth=auth; this.progress=progress; this.guard=guard; }
    private synchronized CloudProvider provider() throws IOException {
        String next=config.get("cloudMode")+"|"+config.get("syncFolder");
        if(current!=null&&key.equals(next)) return current;
        current=switch(config.get("cloudMode")) {
            case "drive" -> new GoogleDrive(auth,config.home,progress,TransferGate.whileGameClosed(guard));
            case "folder" -> {
                if(config.get("syncFolder").isBlank()) throw new IOException("Elegí primero tu carpeta sincronizada en Configuración.");
                Path folder=Path.of(config.get("syncFolder")); FilesEx.separate(config.home,folder); FilesEx.separate(config.saves(),folder);
                yield new FolderCloud(folder,progress,TransferGate.whileGameClosed(guard));
            }
            default -> throw new IOException("Primero elegí dónde guardar tus copias en Configuración.");
        };
        key=next; return current;
    }
    public String deliveredStatus() { return config.get("cloudMode").equals("folder")?"folder":"synced"; }
    public List<Remote> list() throws IOException { return provider().list(); }
    public Remote upload(ArchiveEngine.Snapshot s) throws IOException { return provider().upload(s); }
    public Path download(Remote r,Path dir) throws IOException { return provider().download(r,dir); }
    public void delete(Remote r) throws IOException { provider().delete(r); }
}
