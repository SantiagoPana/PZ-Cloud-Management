package pzcloud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Conservative process guard. Windows Java command lines require the native CIM fallback. */
public class GameGuard {
    public boolean running() throws IOException {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<Long> unknownJava=new ArrayList<>();
        try(var processes=ProcessHandle.allProcesses()) {
            for(ProcessHandle process:processes.toList()) {
                if(process.pid()==ProcessHandle.current().pid()) continue;
                var info=process.info(); String command=info.command().orElse("").toLowerCase(Locale.ROOT);
                String line=info.commandLine().orElse("").toLowerCase(Locale.ROOT);
                String exe;
                try { exe=Path.of(command).getFileName().toString(); } catch(Exception e) { exe=command; }
                if(exe.startsWith("projectzomboid")) return true;
                if(exe.equals("java")||exe.equals("java.exe")||exe.equals("javaw.exe")) {
                    if(isGameLine(line)||command.contains("projectzomboid")) return true;
                    if(windows&&line.isBlank()) unknownJava.add(process.pid());
                }
            }
        } catch(SecurityException e) { throw new IOException("No se pudo comprobar si Project Zomboid está cerrado.",e); }
        return !unknownJava.isEmpty()&&windowsJavaRunning(unknownJava);
    }
    private static boolean isGameLine(String line) {
        line=line.toLowerCase(Locale.ROOT);
        return line.contains("zombie.gamewindow")||line.contains("zombie.network.gameserver")||line.contains("zomboid.jar");
    }
    private boolean windowsJavaRunning(List<Long> ids) throws IOException {
        StringJoiner filter=new StringJoiner(" OR "); ids.forEach(id->filter.add("ProcessId="+id));
        String script="$ErrorActionPreference='Stop'; [Console]::OutputEncoding=New-Object System.Text.UTF8Encoding($false); "
            +"Get-CimInstance Win32_Process -Filter '"+filter+"' | Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress";
        Process query=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectErrorStream(true).start();
        try {
            CompletableFuture<byte[]> output=CompletableFuture.supplyAsync(()-> { try { return query.getInputStream().readNBytes(2*1024*1024+1); } catch(IOException ex) { throw new UncheckedIOException(ex); } });
            if(!query.waitFor(10,TimeUnit.SECONDS)||query.exitValue()!=0) throw new IOException("Windows no pudo comprobar los procesos Java. No se copiará la partida hasta poder verificarlos.");
            byte[] bytes=output.get(2,TimeUnit.SECONDS); if(bytes.length>2*1024*1024) throw new IOException("Respuesta de procesos Windows demasiado grande.");
            String text=new String(bytes,StandardCharsets.UTF_8).trim(); if(text.isBlank()) return false;
            Object parsed=Json.parse(text); List<Object> rows=parsed instanceof List<?>?Json.array(parsed):List.of(parsed);
            for(Object value:rows) {
                Map<String,Object> row=Json.object(value); String line=Json.str(row,"CommandLine");
                if(isGameLine(line)) return true;
                if(line.isBlank()&&ProcessHandle.of(Json.number(row,"ProcessId")).map(ProcessHandle::isAlive).orElse(false)) throw new IOException("Windows no permite comprobar un proceso Java activo. Cerrá ese proceso antes de respaldar o restaurar.");
            }
            return false;
        } catch(InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Comprobación de procesos cancelada.",ex); }
        catch(ExecutionException|TimeoutException ex) { throw new IOException("Windows no pudo consultar los procesos Java.",ex); }
        finally { query.destroyForcibly(); }
    }
    public void requireClosed() throws IOException {
        if(running()) throw new IOException("Cerrá Project Zomboid por completo antes de respaldar o restaurar. La operación queda pendiente.");
    }
}
