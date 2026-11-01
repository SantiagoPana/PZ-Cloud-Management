package pzcloud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Offline snapshot proof of concept. NOT connected to the game.
 * Caller MUST guarantee the world is inactive and no writes are in progress.
 */
public final class LocalBackupEngine {
    private LocalBackupEngine() {}

    public static Path snapshot(Path saveDir, Path backupRoot) throws IOException {
        Path source = saveDir.toRealPath();
        if (!Files.isDirectory(source)) throw new IOException("Save directory missing");
        Path targetRoot = backupRoot.toAbsolutePath().normalize();
        Files.createDirectories(targetRoot);
        targetRoot = targetRoot.toRealPath();
        if (targetRoot.startsWith(source) || source.startsWith(targetRoot)) {
            throw new IOException("Backup directory must be separate from save directory");
        }

        String id = source.getFileName().toString().replaceAll("[^A-Za-z0-9_-]", "_");
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
                .withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        Path output = targetRoot.resolve(id + "-" + stamp + "-" + UUID.randomUUID() + ".zip");
        Path temp = Files.createTempFile(targetRoot, ".pzcloud-", ".part");
        boolean success = false;
        try {
            List<Path> paths = new ArrayList<>();
            try (java.util.stream.Stream<Path> walk = Files.walk(source)) {
                walk.filter(Files::isRegularFile).forEach(paths::add);
            }
            paths.sort(Comparator.naturalOrder());
            try (OutputStream raw = Files.newOutputStream(temp);
                 ZipOutputStream zip = new ZipOutputStream(raw)) {
                for (Path file : paths) {
                    if (Files.isSymbolicLink(file)) throw new IOException("Symlinks not supported: " + file);
                    String entryName = source.relativize(file).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zip);
                    zip.closeEntry();
                }
            }
            // Verify ZIP structure and CRC before publishing.
            try (java.util.zip.ZipFile archive = new java.util.zip.ZipFile(temp.toFile())) {
                Enumeration<? extends ZipEntry> entries = archive.entries();
                byte[] buffer = new byte[8192];
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    try (InputStream in = archive.getInputStream(entry)) {
                        while (in.read(buffer) != -1) { /* CRC validated by ZIP stream */ }
                    }
                }
            }
            try {
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, output);
            }
            success = true;
            return output;
        } finally {
            if (!success) Files.deleteIfExists(temp);
        }
    }
}
