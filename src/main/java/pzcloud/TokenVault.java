package pzcloud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Windows DPAPI CurrentUser; POSIX uses a mode-0600 private file. Never logs tokens. */
final class TokenVault {
    private final Path file;
    private final boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    TokenVault(Path file) { this.file=file; }
    boolean exists() { return Files.exists(file); }
    Map<String,Object> read() throws IOException {
        if(!exists()) return new LinkedHashMap<>();
        byte[] bytes=Files.readAllBytes(file);
        if(bytes.length>65536) throw new IOException("Archivo de credenciales inválido.");
        if(windows) bytes=dpapi(bytes,false);
        return Json.object(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
    }
    void write(Map<String,Object> tokens) throws IOException {
        byte[] bytes=Json.encode(tokens).getBytes(StandardCharsets.UTF_8);
        FilesEx.atomicWrite(file,windows?dpapi(bytes,true):bytes); FilesEx.privateFile(file);
    }
    void delete() throws IOException { Files.deleteIfExists(file); }
    private byte[] dpapi(byte[] bytes, boolean protect) throws IOException {
        String method=protect?"Protect":"Unprotect";
        String script="$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Security; "
            +"$b=[Convert]::FromBase64String([Console]::ReadLine()); "
            +"$r=[Security.Cryptography.ProtectedData]::"+method+"($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser); "
            +"[Console]::Write([Convert]::ToBase64String($r))";
        Process p=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script).redirectErrorStream(true).start();
        try {
            java.util.concurrent.CompletableFuture<byte[]> response=java.util.concurrent.CompletableFuture.supplyAsync(()-> { try { return p.getInputStream().readNBytes(65536); } catch(IOException ex) { throw new UncheckedIOException(ex); } });
            try(OutputStream out=p.getOutputStream()) { out.write((Base64.getEncoder().encodeToString(bytes)+"\n").getBytes(StandardCharsets.US_ASCII)); }
            if(!p.waitFor(20,TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("Windows no respondió al proteger la sesión."); }
            if(p.exitValue()!=0) throw new IOException("Windows no pudo proteger/leer la sesión. Volvé a conectar Google Drive.");
            try { return Base64.getDecoder().decode(new String(response.get(2,TimeUnit.SECONDS),StandardCharsets.US_ASCII).trim()); }
            catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException e) { throw new IOException("Windows no pudo leer la sesión protegida.",e); }
            catch(IllegalArgumentException e) { throw new IOException("Respuesta inválida del almacén de Windows."); }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Operación cancelada.",e); }
        finally { p.destroy(); }
    }
}
