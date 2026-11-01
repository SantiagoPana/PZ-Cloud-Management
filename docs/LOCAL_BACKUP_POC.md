# Local backup proof of concept

The Java sources under `src/main/java/pzcloud` create a ZIP snapshot of an **inactive** save directory. They are intentionally **not yet wired to ZombieBuddy or the PZ UI**. No restore functionality is provided.

## Compile (JDK 17+)

```powershell
javac -d out src/main/java/pzcloud/*.java
java -cp out pzcloud.BackupCli "C:\\Users\\YOU\\Zomboid\\Saves\\Sandbox\\TestWorld" "C:\\PZCloudBackups"
```

Only use a disposable test world, with Project Zomboid fully closed. The caller is responsible for confirming the save is inactive; the engine cannot detect that on its own. Backups must be stored outside the save tree. The proof of concept checks ZIP readability/CRC but does not yet implement restore, SHA-256 manifests, world lifecycle hooks, file mutation detection, or cloud upload. Do not rely on it for irreplaceable saves.

## Next integration gate

Verify ZombieBuddy class loading and find a reliable pre-world-load and post-world-save hook in Build 42.21 before enabling automatic backups. Never copy a live save directory.
