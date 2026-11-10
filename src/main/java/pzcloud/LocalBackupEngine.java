package pzcloud;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Compatibility entry point; snapshots now contain a verified manifest. */
public final class LocalBackupEngine {
    private LocalBackupEngine() {}
    public static Path snapshot(Path saveDir, Path backupRoot) throws IOException {
        return new ArchiveEngine(new GameGuard(),System.out::println).create(saveDir,backupRoot,Map.of(
            "worldId",UUID.nameUUIDFromBytes(saveDir.toAbsolutePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
            "mode","Sandbox","name",saveDir.getFileName().toString(),"reason","manual","deviceId","cli","deviceName","CLI","gameBuild","unknown","parentId","")).archive();
    }
}
