package pzcloud;

import java.nio.file.Path;

/** Manual test harness. Only run with the game fully closed on disposable saves. */
public final class BackupCli {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java pzcloud.BackupCli <inactive-save-directory> <backup-directory>");
            System.exit(2);
        }
        Path result = LocalBackupEngine.snapshot(Path.of(args[0]), Path.of(args[1]));
        System.out.println("Snapshot created: " + result);
    }
}
