package pzcloud;

import java.nio.file.*;
import java.util.*;

/** Install the actual consumer JAR's Workshop resources in an isolated fake Zomboid folder. */
public final class WorkshopInstallSmoke {
    public static void main(String[] args) throws Exception {
        Path temp=Files.createTempDirectory("pzcloud-mod-install-");
        try {
            Config config=new Config(temp.resolve("manager")); config.set("zomboid",temp.resolve("Zomboid").toString());
            GameGuard guard=new GameGuard() { public boolean running() { return false; } };
            SaveManager manager=new SaveManager(config,guard,null,s->{});
            Path mod=config.zomboid().resolve("mods/PZCloudManagement"),translations=mod.resolve("42/media/lua/shared/Translate");
            Files.createDirectories(translations.resolve("ES")); Files.createDirectories(translations.resolve("EN"));
            Files.writeString(translations.resolve("EN/UI_EN.txt"),"obsolete"); Files.writeString(translations.resolve("ES/UI_ES.txt"),"obsolete");
            Files.writeString(translations.resolve("ES/custom.txt"),"keep custom file");
            new GameBridge(manager,()->{},s->{}).installMod();
            for(String file:List.of("42/mod.info","42/poster.png","42/cloud.png","42/media/lua/client/PZCloud/CloudSaves.lua","42/media/lua/shared/Translate/EN/UI.json","42/media/lua/shared/Translate/ES/UI.json","42/media/lua/shared/Translate/AR/UI.json")) {
                try(var source=WorkshopInstallSmoke.class.getResourceAsStream("/mod/PZCloudManagement/"+file)) {
                    if(source==null||!Arrays.equals(source.readAllBytes(),Files.readAllBytes(mod.resolve(file)))) throw new AssertionError("Missing/changed installed resource: "+file);
                }
            }
            if(Files.exists(translations.resolve("EN/UI_EN.txt"))||Files.exists(translations.resolve("ES/UI_ES.txt"))) throw new AssertionError("Obsolete translations survived upgrade");
            if(!Files.readString(translations.resolve("ES/custom.txt")).equals("keep custom file")) throw new AssertionError("Unrelated file changed");
            if(!FilesEx.readJson(translations.resolve("ES/UI.json")).get("UI_PZCloud_Title").equals("Knox Net")) throw new AssertionError("Spanish JSON did not install correctly");
            System.out.println("Workshop mod installation passed: consumer JAR, EN/ES/AR JSON, image and selective upgrade; no personal files or Google account accessed.");
        } finally { FilesEx.deleteTree(temp); }
    }
}
