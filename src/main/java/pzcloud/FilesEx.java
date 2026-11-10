package pzcloud;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;

public final class FilesEx {
    private FilesEx() {}
    public static MessageDigest digest(String type) {
        try { return MessageDigest.getInstance(type); } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static final ThreadLocal<byte[]> BUFFERS=ThreadLocal.withInitial(()->new byte[64*1024]);
    static byte[] buffer() { return BUFFERS.get(); }
    public record Hashes(String sha256,String md5) {}
    public static String hash(Path p, String type) throws IOException {
        MessageDigest d=digest(type);
        try(InputStream in=Files.newInputStream(p)) { byte[] b=buffer(); int n; while((n=in.read(b))!=-1) d.update(b,0,n); }
        return HexFormat.of().formatHex(d.digest());
    }
    public static Hashes hashes(Path p) throws IOException {
        MessageDigest sha=digest("SHA-256"),md5=digest("MD5");
        try(InputStream in=Files.newInputStream(p)) { byte[] b=buffer(); int n; while((n=in.read(b))!=-1) { sha.update(b,0,n); md5.update(b,0,n); } }
        return new Hashes(HexFormat.of().formatHex(sha.digest()),HexFormat.of().formatHex(md5.digest()));
    }
    public static void atomicWrite(Path target, String content) throws IOException { atomicWrite(target, content.getBytes(StandardCharsets.UTF_8)); }
    public static void atomicWrite(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), ".write-", ".tmp");
        try {
            privateFile(temp); Files.write(temp,content);
            try(var c = java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE)) { c.force(true); }
            move(temp,target,true);
        } finally { Files.deleteIfExists(temp); }
    }
    public static void move(Path source, Path target, boolean replace) throws IOException {
        CopyOption[] atomic = replace ? new CopyOption[]{StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING} : new CopyOption[]{StandardCopyOption.ATOMIC_MOVE};
        try { Files.move(source,target,atomic); }
        catch(AtomicMoveNotSupportedException e) {
            if(replace) Files.move(source,target,StandardCopyOption.REPLACE_EXISTING); else Files.move(source,target);
        }
    }
    public static void privateFile(Path p) throws IOException {
        if(Files.getFileAttributeView(p, PosixFileAttributeView.class) != null) Files.setPosixFilePermissions(p, PosixFilePermissions.fromString("rw-------"));
    }
    public static Map<String,Object> readJson(Path p) throws IOException {
        if(Files.size(p) > 16*1024*1024) throw new IOException("JSON file too large: " + p.getFileName());
        return Json.object(Json.parse(Files.readString(p)));
    }
    public static void json(Path p, Object value) throws IOException { atomicWrite(p, Json.encode(value)); }
    public static void deleteTree(Path root) throws IOException {
        if(!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try(var stream = Files.walk(root)) {
            for(Path p : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
    /** Resolve through existing ancestors to catch junctions/symlinks, including a not-yet-created leaf. */
    public static Path canonical(Path p) throws IOException {
        p = p.toAbsolutePath().normalize();
        if(Files.exists(p,LinkOption.NOFOLLOW_LINKS)) return p.toRealPath();
        Path parent=p.getParent();
        if(parent==null) throw new IOException("Invalid path");
        return canonical(parent).resolve(p.getFileName());
    }
    public static void separate(Path first, Path second) throws IOException {
        Path a=canonical(first), b=canonical(second);
        if(a.startsWith(b)||b.startsWith(a)) throw new IOException("Las partidas y los respaldos deben estar en carpetas separadas.");
    }
    /** Portable Windows-safe names also prevent ZIP traversal, ADS, device files and case collisions. */
    public static void safeName(String name) throws IOException {
        if(name.isBlank() || name.length()>240 || name.equals(".") || name.equals("..") || name.endsWith(".") || name.endsWith(" ") || name.matches(".*[<>:\"/\\\\|?*\\x00-\\x1f].*")) throw new IOException("Nombre de archivo no seguro: " + name);
        String stem=name.split("\\.",2)[0].toUpperCase(Locale.ROOT);
        if(stem.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) throw new IOException("Nombre reservado: " + name);
    }
    public static void safeRelative(String name) throws IOException {
        if(name.isEmpty() || name.length()>2048 || name.startsWith("/") || name.endsWith("/")) throw new IOException("Invalid archive path");
        for(String part:name.split("/",-1)) safeName(part);
    }
    public static String human(long bytes) {
        if(bytes<1024) return bytes + " B";
        int p=(int)(Math.log(bytes)/Math.log(1024));
        return String.format(Locale.ROOT,"%.1f %s",bytes/Math.pow(1024,p),new String[]{"B","KB","MB","GB","TB"}[Math.min(4,p)]);
    }
}
