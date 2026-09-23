package com.assetiq.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("Filesystem file storage")
class FilesystemFileStorageServiceTest {

    private FilesystemFileStorageService serviceAt(Path baseDir) {
        FilesystemFileStorageService svc = new FilesystemFileStorageService(baseDir.toString());
        svc.init();
        return svc;
    }

    private static final byte[] PAYLOAD = "contract body".getBytes(StandardCharsets.UTF_8);

    // ── Durability ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("durability")
    class Durability {

        @Test
        @DisplayName("an object survives a full restart of the service")
        void survivesRestart(@TempDir Path baseDir) {
            // The whole reason this backend exists: InMemoryFileStorageService
            // loses every uploaded file when the process goes away.
            String key = "attachments/" + java.util.UUID.randomUUID() + "/contract.pdf";

            FilesystemFileStorageService before = serviceAt(baseDir);
            before.store(key, PAYLOAD, "application/pdf", "contract.pdf", Map.of());

            // Simulate a restart: a brand-new instance over the same directory,
            // sharing nothing with the first.
            FilesystemFileStorageService after = serviceAt(baseDir);

            Optional<StoredObject> reloaded = after.get(key);
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().bytes()).isEqualTo(PAYLOAD);
            assertThat(reloaded.get().contentType()).isEqualTo("application/pdf");
            assertThat(reloaded.get().filename()).isEqualTo("contract.pdf");
        }

        @Test
        @DisplayName("creates the base directory when it does not exist")
        void createsBaseDirectory(@TempDir Path parent) throws Exception {
            Path baseDir = parent.resolve("nested/storage/root");
            assertThat(Files.exists(baseDir)).isFalse();

            FilesystemFileStorageService svc = serviceAt(baseDir);

            assertThat(Files.isDirectory(baseDir)).isTrue();
            assertThat(svc.baseDirectory()).isEqualTo(baseDir.toRealPath());
        }

        @Test
        @DisplayName("overwriting an object replaces both bytes and metadata")
        void overwriteReplacesMetadata(@TempDir Path baseDir) {
            FilesystemFileStorageService svc = serviceAt(baseDir);
            svc.store("k/one", PAYLOAD, "application/pdf", "a.pdf", Map.of());
            svc.store("k/one", "csv,data".getBytes(StandardCharsets.UTF_8), "text/csv", "b.csv", Map.of());

            StoredObject stored = svc.get("k/one").orElseThrow();
            assertThat(new String(stored.bytes(), StandardCharsets.UTF_8)).isEqualTo("csv,data");
            assertThat(stored.contentType()).isEqualTo("text/csv");
        }

        @Test
        @DisplayName("delete removes the object and its metadata sidecar")
        void deleteRemovesFile(@TempDir Path baseDir) {
            FilesystemFileStorageService svc = serviceAt(baseDir);
            svc.store("k/gone.pdf", PAYLOAD, "application/pdf", "gone.pdf", Map.of());
            assertThat(svc.get("k/gone.pdf")).isPresent();

            svc.delete("k/gone.pdf");

            assertThat(svc.get("k/gone.pdf")).isEmpty();
            assertThat(Files.exists(baseDir.resolve("k/gone.pdf"))).isFalse();
            assertThat(Files.exists(
                    baseDir.resolve("k/gone.pdf" + FilesystemFileStorageService.META_SUFFIX))).isFalse();
        }

        @Test
        @DisplayName("deleting an object that is not there is not an error")
        void deleteMissingIsNoop(@TempDir Path baseDir) {
            FilesystemFileStorageService svc = serviceAt(baseDir);
            assertThatCode(() -> svc.delete("k/never-existed")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("reading an absent key returns empty rather than throwing")
        void missingKeyIsEmpty(@TempDir Path baseDir) {
            assertThat(serviceAt(baseDir).get("k/absent")).isEmpty();
        }

        @Test
        @DisplayName("no presigned URL — callers fall back to the streaming endpoint")
        void noPresignedUrl(@TempDir Path baseDir) {
            assertThat(serviceAt(baseDir)
                    .createPresignedGetUrl("k/x", "x.pdf", "application/pdf", Duration.ofMinutes(15)))
                    .isEmpty();
        }
    }

    // ── Path traversal ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("path traversal")
    class PathTraversal {

        /**
         * Object keys are assembled from user-supplied filenames and tenant ids.
         * If any of these resolved, an upload could write over /etc or read a
         * file outside the storage root.
         */
        @ParameterizedTest(name = "rejects \"{0}\"")
        @ValueSource(strings = {
                "../escaped.txt",
                "../../../../etc/passwd",
                "attachments/../../etc/passwd",
                "attachments/../../../outside.pdf",
                "a/b/../../../c",
                "./../../x",
                "/etc/passwd",
                "/tmp/absolute.pdf",
                "\\\\windows\\\\system32\\\\evil.dll"
        })
        void rejectsTraversalAndAbsoluteKeys(String key, @TempDir Path baseDir) {
            FilesystemFileStorageService svc = serviceAt(baseDir);

            assertThatThrownBy(() -> svc.resolveKey(key))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a traversal store() writes nothing outside the base directory")
        void traversalStoreWritesNothing(@TempDir Path parent) throws Exception {
            Path baseDir = parent.resolve("base");
            Path outside = parent.resolve("outside.txt");
            FilesystemFileStorageService svc = serviceAt(baseDir);

            assertThatThrownBy(() -> svc.store("../outside.txt", PAYLOAD,
                                               "text/plain", "outside.txt", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(Files.exists(outside)).isFalse();
        }

        @Test
        @DisplayName("a traversal get() returns empty instead of reading an outside file")
        void traversalGetReadsNothing(@TempDir Path parent) throws Exception {
            Path baseDir = parent.resolve("base");
            Path secret = parent.resolve("secret.txt");
            Files.writeString(secret, "top secret");
            FilesystemFileStorageService svc = serviceAt(baseDir);

            assertThat(svc.get("../secret.txt")).isEmpty();
            assertThat(Files.readString(secret)).isEqualTo("top secret");
        }

        @Test
        @DisplayName("a traversal delete() does not remove a file outside the base directory")
        void traversalDeleteRemovesNothing(@TempDir Path parent) throws Exception {
            Path baseDir = parent.resolve("base");
            Path precious = parent.resolve("precious.txt");
            Files.writeString(precious, "keep me");
            FilesystemFileStorageService svc = serviceAt(baseDir);

            svc.delete("../precious.txt");

            assertThat(Files.exists(precious)).isTrue();
        }

        @Test
        @DisplayName("a symlink planted inside the base directory cannot be used to escape")
        void rejectsSymlinkEscape(@TempDir Path parent) throws Exception {
            Path baseDir = parent.resolve("base");
            Path outside = parent.resolve("outside");
            Files.createDirectories(outside);
            FilesystemFileStorageService svc = serviceAt(baseDir);

            Path link = baseDir.resolve("link");
            try {
                Files.createSymbolicLink(link, outside);
            } catch (UnsupportedOperationException | java.io.IOException notPermitted) {
                assumeTrue(false, "Filesystem does not allow creating symlinks here");
            }

            // normalize() alone would happily return base/link/escaped.txt, which
            // really is parent/outside/escaped.txt.
            assertThatThrownBy(() -> svc.store("link/escaped.txt", PAYLOAD,
                                               "text/plain", "escaped.txt", Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(Files.exists(outside.resolve("escaped.txt"))).isFalse();
        }

        @Test
        @DisplayName("rejects a blank key and a key containing a NUL byte")
        void rejectsDegenerateKeys(@TempDir Path baseDir) {
            FilesystemFileStorageService svc = serviceAt(baseDir);

            assertThatThrownBy(() -> svc.resolveKey(null)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> svc.resolveKey("  ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> svc.resolveKey("ok/\u0000bad"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("a legitimate nested key still resolves under the base directory")
        void acceptsLegitimateKey(@TempDir Path baseDir) {
            FilesystemFileStorageService svc = serviceAt(baseDir);
            Path resolved = svc.resolveKey("attachments/org-1/contract/abc-def.pdf");
            // Compared as paths, not as AssertJ Path assertions: the object does
            // not exist yet, and PathAssert canonicalises.
            assertThat(resolved.startsWith(svc.baseDirectory())).isTrue();
            assertThat(resolved.toString()).endsWith("attachments/org-1/contract/abc-def.pdf");
        }
    }

    // ── Backup and restore ────────────────────────────────────────────────────

    @Test
    @DisplayName("the base directory is a self-contained backup unit — copying it restores everything")
    void directoryIsASelfContainedBackupUnit(@TempDir Path original,
                                             @TempDir Path restored) throws Exception {
        // What scripts/backup.sh and scripts/restore.sh do is tar this directory
        // and untar it somewhere else. That only works if everything needed to
        // serve an object lives inside it — bytes AND content type AND filename.
        // If the content type were held anywhere else (a database column, a cache)
        // a restore would bring back files that download as the wrong type.
        FilesystemFileStorageService before = serviceAt(original);
        String key = "attachments/" + java.util.UUID.randomUUID() + "/contract/evidence.pdf";
        before.store(key, PAYLOAD, "application/pdf", "evidence.pdf", Map.of());

        copyTree(original, restored);

        FilesystemFileStorageService after = serviceAt(restored);
        StoredObject object = after.get(key).orElseThrow(
                () -> new AssertionError("the restored copy does not contain the object"));

        assertThat(object.bytes()).isEqualTo(PAYLOAD);
        assertThat(object.contentType()).isEqualTo("application/pdf");
        assertThat(object.filename()).isEqualTo("evidence.pdf");
    }

    private static void copyTree(Path from, Path to) throws Exception {
        try (var paths = Files.walk(from)) {
            for (Path source : paths.toList()) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target,
                               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ── Tenant isolation on disk ──────────────────────────────────────────────

    @Test
    @DisplayName("per-org key prefixes keep tenants in separate directories on disk")
    void orgPrefixesAreHonouredOnDisk(@TempDir Path baseDir) {
        FilesystemFileStorageService svc = serviceAt(baseDir);
        String orgA = java.util.UUID.randomUUID().toString();
        String orgB = java.util.UUID.randomUUID().toString();

        svc.store("attachments/" + orgA + "/contract/x.pdf", "A".getBytes(StandardCharsets.UTF_8),
                  "application/pdf", "x.pdf", Map.of());
        svc.store("attachments/" + orgB + "/contract/x.pdf", "B".getBytes(StandardCharsets.UTF_8),
                  "application/pdf", "x.pdf", Map.of());

        assertThat(svc.get("attachments/" + orgA + "/contract/x.pdf").orElseThrow().bytes())
                .isEqualTo("A".getBytes(StandardCharsets.UTF_8));
        assertThat(svc.get("attachments/" + orgB + "/contract/x.pdf").orElseThrow().bytes())
                .isEqualTo("B".getBytes(StandardCharsets.UTF_8));
        assertThat(Files.isDirectory(baseDir.resolve("attachments").resolve(orgA))).isTrue();
        assertThat(Files.isDirectory(baseDir.resolve("attachments").resolve(orgB))).isTrue();
    }

    // ── Permissions and startup ───────────────────────────────────────────────

    @Test
    @DisplayName("stored files are not world-readable")
    void filesAreNotWorldReadable(@TempDir Path baseDir) throws Exception {
        assumeTrue(baseDir.getFileSystem().supportedFileAttributeViews().contains("posix"),
                   "POSIX permissions not supported on this filesystem");

        FilesystemFileStorageService svc = serviceAt(baseDir);
        svc.store("k/private.pdf", PAYLOAD, "application/pdf", "private.pdf", Map.of());

        Set<PosixFilePermission> perms =
                Files.getPosixFilePermissions(baseDir.resolve("k/private.pdf"));

        assertThat(perms).doesNotContain(
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE);
        assertThat(perms).contains(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    @Test
    @DisplayName("refuses to start when the base directory is not writable")
    void failsFastOnUnwritableDirectory(@TempDir Path parent) throws Exception {
        assumeTrue(parent.getFileSystem().supportedFileAttributeViews().contains("posix"),
                   "POSIX permissions not supported on this filesystem");
        assumeTrue(!"root".equals(System.getProperty("user.name")), "root ignores permission bits");

        Path readOnly = parent.resolve("read-only");
        Files.createDirectories(readOnly);
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.readOnly());

        try {
            assertThatThrownBy(() -> serviceAt(readOnly))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("STORAGE STARTUP FAILURE")
                    .hasMessageContaining("APP_STORAGE_FILESYSTEM_BASE_DIR");
        } finally {
            Files.setPosixFilePermissions(readOnly,
                    java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    @DisplayName("refuses to start when base-dir is blank")
    void failsFastOnBlankBaseDir() {
        assertThatThrownBy(() -> new FilesystemFileStorageService("   ").init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base-dir is empty");
    }

    /** Local alias so the read-only helper reads clearly above. */
    private static final class PosixFilePermissions {
        static Set<PosixFilePermission> readOnly() {
            return java.nio.file.attribute.PosixFilePermissions.fromString("r-xr-xr-x");
        }
    }
}
