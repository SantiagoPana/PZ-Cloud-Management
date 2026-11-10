package pzcloud;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;

/** Deliberately conservative: no snapshots/restores while any PZ client or server is open. */
public class GameGuard {
    public boolean running() throws IOException {
        try(var ps=ProcessHandle.allProcesses()) {
            return ps.anyMatch(p -> {
                if(p.pid()==ProcessHandle.current().pid()) return false;
                String command=p.info().command().orElse("").toLowerCase(Locale.ROOT);
                String line=p.info().commandLine().orElse("").toLowerCase(Locale.ROOT);
                String exe;
                try { exe=Path.of(command).getFileName().toString(); } catch(Exception e) { exe=command; }
                if(exe.startsWith("projectzomboid")) return true;
                if(exe.equals("java")||exe.equals("java.exe")||exe.equals("javaw.exe")) {
                    return line.contains("zombie.gamewindow")||line.contains("zombie.network.gameserver")||line.contains("zomboid.jar");
                }
                return false;
            });
        } catch(SecurityException e) { throw new IOException("No se pudo comprobar si Project Zomboid está cerrado.",e); }
    }
    public void requireClosed() throws IOException {
        if(running()) throw new IOException("Cerrá Project Zomboid por completo antes de respaldar o restaurar. La operación queda pendiente.");
    }
}
