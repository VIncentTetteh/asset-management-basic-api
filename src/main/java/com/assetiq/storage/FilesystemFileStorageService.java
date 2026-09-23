package com.assetiq.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Durable storage backend that writes objects to a local directory tree.
 *
 * <p>This is the default backend for a self-hosted installation, where the base
 * directory is a mounted volume. It exists because the alternative — the
 * {@link InMemoryFileStorageService} heap map — loses every uploaded document on
 * restart, is invisible to a second replica, and is never evicted. A storage
 * backend that forgets is not a storage backend.</p>
 *
 * <h2>Layout</h2>
 * <pre>
 *   {base-dir}/{key}         the object bytes
 *   {base-dir}/{key}.meta    content type + original filename
 * </pre>
 * <p>The sidecar exists because a filesystem, unlike S3, has nowhere to hang the
 * content type. It is written first-class rather than inferred from the
 * extension, so what comes back out of {@link #get(String)} is what the uploader
 * declared and the upload validator verified — never a guess made at read time.</p>
 *
 * <h2>Path traversal</h2>
 * <p>Object keys are assembled from user-supplied filenames and tenant ids, so
 * every key is untrusted input. {@link #resolveKey(String)} rejects absolute
 * paths and NUL bytes outright, normalises the candidate, and refuses anything
 * that does not remain under the canonicalised base directory. The check is
 * repeated against the real path of the nearest existing ancestor, so a symlink
 * planted inside the base directory cannot be used to escape it either.</p>
 *
 * <h2>Permissions</h2>
 * <p>Directories are created {@code rwx------} and files {@code rw-------} on
 * POSIX systems: uploaded documents routinely contain contracts, invoices and
 * compliance evidence, and nothing else on the host has any business reading
 * them. On a filesystem without POSIX permissions the code falls back to the
 * {@code java.io.File} owner-only flags.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.storage.filesystem", name = "enabled", havingValue = "true")
public class FilesystemFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FilesystemFileStorageService.class);

    /** Suffix of the sidecar holding an object's content type and original filename. */
    static final String META_SUFFIX = ".meta";

    private static final String META_CONTENT_TYPE = "contentType";
    private static final String META_FILENAME = "filename";

    private static final Set<PosixFilePermission> DIR_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");

    private final String configuredBaseDir;

    /** Canonicalised base directory. Every resolved path must stay under it. */
    private Path baseDir;

    public FilesystemFileStorageService(
            @Value("${app.storage.filesystem.base-dir:./data/assetiq-storage}") String configuredBaseDir) {
        this.configuredBaseDir = configuredBaseDir;
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    /**
     * Creates the base directory if it is absent and refuses to start if it
     * cannot be written to. A storage directory that turns out to be read-only
     * is discovered on the first upload otherwise — which is to say, in
     * production, by a user.
     */
    @PostConstruct
    void init() {
        if (configuredBaseDir == null || configuredBaseDir.isBlank()) {
            throw new IllegalStateException(
                    "[STORAGE STARTUP FAILURE] app.storage.filesystem.enabled=true but "
                    + "app.storage.filesystem.base-dir is empty.\n"
                    + "Fix: set APP_STORAGE_FILESYSTEM_BASE_DIR to a directory on a persistent volume.");
        }

        Path requested = Paths.get(configuredBaseDir.trim()).toAbsolutePath().normalize();
        try {
            if (!Files.exists(requested)) {
                createDirectories(requested);
            }
            if (!Files.isDirectory(requested)) {
                throw new IllegalStateException("not a directory");
            }
            this.baseDir = requested.toRealPath();
            assertWritable(this.baseDir);
        } catch (IllegalStateException e) {
            throw new IllegalStateException(storageUnusable(requested, e.getMessage()), e);
        } catch (IOException | SecurityException e) {
            throw new IllegalStateException(
                    storageUnusable(requested, e.getClass().getSimpleName() + ": " + e.getMessage()), e);
        }

        log.info("[FilesystemStorage] Using durable file storage at {}", baseDir);
    }

    private String storageUnusable(Path requested, String reason) {
        return "[STORAGE STARTUP FAILURE] Cannot use app.storage.filesystem.base-dir="
               + requested + " — " + reason + ".\n"
               + "AssetIQ needs a writable directory on a persistent volume to store uploaded "
               + "documents, generated reports and import files.\n"
               + "Fix: create the directory and grant the application's user write access "
               + "(e.g. mkdir -p " + requested + " && chown $APP_UID " + requested + "), "
               + "or point APP_STORAGE_FILESYSTEM_BASE_DIR somewhere writable.";
    }

    private void assertWritable(Path dir) throws IOException {
        if (!Files.isWritable(dir)) {
            throw new IllegalStateException("directory is not writable by this process");
        }
        // isWritable() consults permission bits, which read-only mounts and some
        // container filesystems report optimistically. Prove it instead.
        Path probe = dir.resolve(".assetiq-write-probe-" + ProcessHandle.current().pid());
        try {
            Files.write(probe, new byte[0]);
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    // ── FileStorageService ────────────────────────────────────────────────────

    @Override
    public StoredObject store(String key, byte[] bytes, String contentType,
                              String filename, Map<String, String> metadata) {
        Path target = resolveKey(key);
        try {
            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                createDirectories(parent);
            }
            // Write to a temporary sibling and move it into place so a crash
            // mid-write never leaves a half-written object readable.
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp-"
                                             + java.util.UUID.randomUUID());
            try (OutputStream out = Files.newOutputStream(tmp)) {
                out.write(bytes == null ? new byte[0] : bytes);
            }
            restrictFilePermissions(tmp);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            writeSidecar(target, contentType, filename);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to write object to filesystem storage (key=" + key + "): " + e.getMessage(), e);
        }
        return new StoredObject(contentType, filename, bytes);
    }

    @Override
    public Optional<StoredObject> get(String key) {
        Path target;
        try {
            target = resolveKey(key);
        } catch (IllegalArgumentException rejected) {
            log.warn("[FilesystemStorage] Rejected read of unsafe key: {}", rejected.getMessage());
            return Optional.empty();
        }
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            Properties meta = readSidecar(target);
            return Optional.of(new StoredObject(
                    meta.getProperty(META_CONTENT_TYPE),
                    meta.getProperty(META_FILENAME),
                    bytes));
        } catch (IOException e) {
            log.warn("[FilesystemStorage] Failed to read {}: {}", target, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Always empty: there is no signed-URL equivalent for a local directory.
     * Callers fall back to the application's own streaming endpoint, which is
     * the right answer here anyway — it is the only layer that can check the
     * caller's tenant before handing over the bytes.
     */
    @Override
    public Optional<String> createPresignedGetUrl(String key, String filename,
                                                  String contentType, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public void delete(String key) {
        Path target;
        try {
            target = resolveKey(key);
        } catch (IllegalArgumentException rejected) {
            log.warn("[FilesystemStorage] Rejected delete of unsafe key: {}", rejected.getMessage());
            return;
        }
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(sidecarOf(target));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to delete object from filesystem storage (key=" + key + "): " + e.getMessage(), e);
        }
    }

    // ── Path safety ───────────────────────────────────────────────────────────

    /** Visible for tests: the canonicalised root every key resolves under. */
    Path baseDirectory() {
        return baseDir;
    }

    /**
     * Turns an untrusted object key into a path that is provably inside the base
     * directory, or throws.
     *
     * @throws IllegalArgumentException if the key is absent, absolute, contains a
     *         NUL byte, or escapes the base directory by traversal or symlink.
     */
    Path resolveKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        if (key.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Storage key contains a NUL byte");
        }

        Path candidate;
        try {
            candidate = Paths.get(key);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Storage key is not a valid path: " + key);
        }
        if (candidate.isAbsolute() || key.startsWith("/") || key.startsWith("\\")) {
            throw new IllegalArgumentException("Storage key must be relative, got: " + key);
        }
        if (candidate.getRoot() != null) {
            // e.g. "C:\\Windows\\..." on Windows, which is not reported absolute
            // when the drive is omitted from the path string.
            throw new IllegalArgumentException("Storage key must not name a filesystem root: " + key);
        }

        Path resolved = baseDir.resolve(candidate).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Storage key escapes the storage directory: " + key);
        }
        assertNoSymlinkEscape(resolved, key);
        return resolved;
    }

    /**
     * Re-checks containment against real paths. {@link Path#normalize()} works on
     * the path string alone, so on its own it would happily resolve through a
     * symlink that points outside the base directory.
     */
    private void assertNoSymlinkEscape(Path resolved, String key) {
        Path existing = resolved;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return;
        }
        try {
            Path real = existing.toRealPath();
            if (!real.startsWith(baseDir)) {
                throw new IllegalArgumentException(
                        "Storage key resolves outside the storage directory via a symlink: " + key);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Storage key could not be canonicalised: " + key + " (" + e.getMessage() + ")");
        }
    }

    // ── Sidecar + permissions ─────────────────────────────────────────────────

    private Path sidecarOf(Path target) {
        return target.resolveSibling(target.getFileName() + META_SUFFIX);
    }

    private void writeSidecar(Path target, String contentType, String filename) throws IOException {
        Properties meta = new Properties();
        if (contentType != null && !contentType.isBlank()) {
            meta.setProperty(META_CONTENT_TYPE, contentType);
        }
        if (filename != null && !filename.isBlank()) {
            meta.setProperty(META_FILENAME, filename);
        }
        Path sidecar = sidecarOf(target);
        try (OutputStream out = Files.newOutputStream(sidecar)) {
            meta.store(out, "AssetIQ object metadata");
        }
        restrictFilePermissions(sidecar);
    }

    private Properties readSidecar(Path target) {
        Properties meta = new Properties();
        Path sidecar = sidecarOf(target);
        if (!Files.isRegularFile(sidecar)) {
            return meta;
        }
        try (InputStream in = Files.newInputStream(sidecar)) {
            meta.load(in);
        } catch (IOException e) {
            log.warn("[FilesystemStorage] Unreadable metadata sidecar {}: {}", sidecar, e.getMessage());
        }
        return meta;
    }

    private void createDirectories(Path dir) throws IOException {
        if (supportsPosix(dir)) {
            try {
                Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(DIR_PERMISSIONS));
                return;
            } catch (FileAlreadyExistsException alreadyThere) {
                return;
            } catch (UnsupportedOperationException notPosix) {
                // fall through
            }
        }
        Files.createDirectories(dir);
        ownerOnly(dir);
    }

    private void restrictFilePermissions(Path file) {
        if (supportsPosix(file)) {
            try {
                Files.setPosixFilePermissions(file, FILE_PERMISSIONS);
                return;
            } catch (IOException | UnsupportedOperationException e) {
                log.debug("[FilesystemStorage] Could not set POSIX permissions on {}: {}",
                          file, e.getMessage());
            }
        }
        ownerOnly(file);
    }

    private void ownerOnly(Path path) {
        java.io.File f = path.toFile();
        f.setReadable(false, false);
        f.setWritable(false, false);
        f.setExecutable(false, false);
        f.setReadable(true, true);
        f.setWritable(true, true);
        if (Files.isDirectory(path)) {
            f.setExecutable(true, true);
        }
    }

    private boolean supportsPosix(Path path) {
        Path probe = path;
        while (probe != null && !Files.exists(probe)) {
            probe = probe.getParent();
        }
        return probe != null
               && probe.getFileSystem().supportedFileAttributeViews().contains("posix");
    }

    /** Visible for tests. */
    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
