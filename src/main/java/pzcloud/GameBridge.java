package pzcloud;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Fixed file protocol in Zomboid/Lua; the mod can request listing/download, never restore. */
public final class GameBridge {
    private final SaveManager manager;
    private final Runnable showWindow;
    private final Consumer<String> log;
    private String last="";
    public GameBridge(SaveManager manager,Runnable showWindow,Consumer<String> log) throws IOException {
        this.manager=manager; this.showWindow=showWindow; this.log=log;
        Path state=manager.config.home.resolve("bridge-state.json");
        if(Files.exists(state)) last=Json.str(FilesEx.readJson(state),"last");
    }
    void showWindow() { showWindow.run(); }
    private Path lua(String name) throws IOException { Path dir=manager.config.zomboid().resolve("Lua"); Files.createDirectories(dir); return dir.resolve(name); }
    public void publish(List<CloudProvider.Remote> versions) throws IOException {
        StringBuilder text=new StringBuilder();
        for(var r:versions) text.append(clean(r.fileId())).append('|').append(clean(r.get("saveName"))).append('|').append(clean(r.get("mode"))).append('|').append(clean(r.get("created"))).append('|').append(clean(r.get("device"))).append('|').append(FilesEx.human(r.bytes())).append('\n');
        FilesEx.atomicWrite(lua("pzcloud-catalog.txt"),text.toString());
    }
    private static String clean(String s) { return s.replace('|',' ').replace('\n',' ').replace('\r',' '); }
    public void tick() throws IOException {
        Path request=lua("pzcloud-request.txt"); if(!Files.exists(request)||Files.size(request)>1024) return;
        String[] parts=Files.readString(request).trim().split("\\|",-1);
        if(parts.length<3||!parts[0].equals("1")||!parts[1].matches("[A-Za-z0-9_-]{1,100}")||parts[1].equals(last)) return;
        last=parts[1]; FilesEx.json(manager.config.home.resolve("bridge-state.json"),Map.of("last",last));
        try {
            switch(parts[2]) {
                case "OPEN" -> showWindow.run();
                case "LIST" -> publish(manager.cloud.list());
                case "DOWNLOAD" -> {
                    if(parts.length!=4||!parts[3].matches("[A-Za-z0-9_-]+")) throw new IOException("Solicitud de descarga inválida.");
                    CloudProvider.Remote remote=manager.cloud.list().stream().filter(r->r.fileId().equals(parts[3])).findFirst().orElseThrow(()->new IOException("La versión ya no está en la nube."));
                    manager.download(remote);
                }
                default -> throw new IOException("Solicitud desconocida.");
            }
            FilesEx.atomicWrite(lua("pzcloud-status.txt"),last+"|OK|Listo. Para restaurar, cerrá Zomboid y usá el gestor.\n");
        } catch(IOException ex) {
            FilesEx.atomicWrite(lua("pzcloud-status.txt"),last+"|ERROR|"+clean(ex.getMessage())+"\n"); log.accept(ex.getMessage());
        }
    }
    public void installMod() throws IOException {
        manager.guard.requireClosed(); Path modRoot=manager.config.zomboid().resolve("mods/PZCloudManagement"); Files.createDirectories(modRoot.resolve("common"));
        for(String file:List.of("42/mod.info","42/poster.png","42/cloud.png","42/media/lua/client/PZCloud/CloudSaves.lua","42/media/lua/shared/Translate/EN/UI.json","42/media/lua/shared/Translate/ES/UI.json","42/media/lua/shared/Translate/AR/UI.json")) {
            try(var in=GameBridge.class.getResourceAsStream("/mod/PZCloudManagement/"+file)) {
                if(in==null) throw new IOException("El paquete no incluye el mod. Ejecutá scripts/build.ps1 antes de instalar.");
                FilesEx.atomicWrite(modRoot.resolve(file),in.readAllBytes());
            }
        }
        // Remove only our superseded translations when upgrading a local installation.
        for(String old:List.of("EN/UI_EN.txt","ES/UI_ES.txt")) Files.deleteIfExists(modRoot.resolve("42/media/lua/shared/Translate/"+old));
        log.accept("Mod instalado. Activá Knox Net en el menú Mods de Build 42.");
    }
}
