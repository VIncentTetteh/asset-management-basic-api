package com.assetiq.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The single gate every user-supplied file passes through before it is stored.
 *
 * <p>Uploads are the classic stored-XSS and content-confusion surface, and the
 * same three questions have to be answered identically everywhere a file can
 * enter the system — hence one component rather than a copy of the rules per
 * controller.</p>
 *
 * <h2>What it enforces</h2>
 * <ol>
 *   <li><b>Size.</b> Checked here as well as by Spring's multipart limits, so
 *       the answer is a clear 400 rather than a container-level failure, and so
 *       a caller that does not come through multipart is still bounded.</li>
 *   <li><b>An allow-list of content types.</b> Documents and raster images only.
 *       <b>SVG and HTML are deliberately absent</b>: both are script-bearing
 *       documents that execute in the origin that serves them, which would make
 *       every attachment a stored-XSS vector against the app itself.</li>
 *   <li><b>The declared type actually matches the bytes.</b> {@code Content-Type}
 *       on a multipart part is whatever the client typed. A file claiming
 *       {@code application/pdf} while starting {@code <script>} is rejected here,
 *       not discovered by a browser later.</li>
 * </ol>
 *
 * <p><b>Note what this is not.</b> None of it is malware scanning: it bounds
 * what a file can be interpreted as, not what it contains. A structurally valid
 * PDF or XLSX carrying a malicious payload passes every check here. The hook for
 * scanning is {@link FileContentScanner} — contribute a bean and it is enforced
 * on every upload path, because every upload path goes through this class. With
 * no such bean, uploads are unscanned, and a deployment with a regulatory
 * obligation to scan should put a scanning gateway in front of AssetIQ.</p>
 */
@Component
public class UploadValidator {

    /** Default ceiling for a single uploaded file. Mirrored by spring.servlet.multipart.max-file-size. */
    public static final long DEFAULT_MAX_FILE_SIZE = 25L * 1024 * 1024;

    /**
     * Content types accepted for upload. Anything absent is refused.
     *
     * <p>{@code image/svg+xml}, {@code text/html}, {@code application/xhtml+xml}
     * and {@code text/xml} are absent on purpose: they carry script that runs in
     * the serving origin. {@code X-Content-Type-Options: nosniff} and an
     * attachment disposition on download defend the same boundary, but not
     * accepting them in the first place is the part that does not depend on a
     * response header being correct on every path.</p>
     */
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            // Raster images only — see above for why SVG is not here.
            "image/jpeg", "image/png", "image/gif", "image/webp",
            // Office
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            // Plain text
            "text/plain", "text/csv"
    );

    /**
     * Content types that are refused with a specific message, because "unsupported
     * file type" invites the user to rename the file and try again.
     */
    private static final Map<String, String> EXPLICITLY_REFUSED = Map.of(
            "image/svg+xml", "SVG",
            "text/html", "HTML",
            "application/xhtml+xml", "XHTML",
            "text/xml", "XML",
            "application/xml", "XML",
            "application/x-shockwave-flash", "Flash"
    );

    /** Leading bytes that identify a script-bearing document whatever it claims to be. */
    private static final String[] SCRIPTABLE_TEXT_PREFIXES = {
            "<!doctype html", "<html", "<script", "<?xml", "<svg", "<!entity"
    };

    private final long maxFileSize;

    /** Absent by default — see {@link FileContentScanner}. Uploads are not scanned. */
    private final Optional<FileContentScanner> scanner;

    public UploadValidator(
            @Value("${app.upload.max-file-size-bytes:" + DEFAULT_MAX_FILE_SIZE + "}") long maxFileSize) {
        this(maxFileSize, Optional.empty());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UploadValidator(
            @Value("${app.upload.max-file-size-bytes:" + DEFAULT_MAX_FILE_SIZE + "}") long maxFileSize,
            Optional<FileContentScanner> scanner) {
        this.maxFileSize = maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_FILE_SIZE;
        this.scanner = scanner;
    }

    public long maxFileSize() {
        return maxFileSize;
    }

    /**
     * Validates an uploaded file and returns its bytes along with the content
     * type that survived validation.
     *
     * @throws IllegalArgumentException with a message safe to return to the caller
     */
    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty.");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    "File size " + file.getSize() + " bytes exceeds the "
                    + (maxFileSize / (1024 * 1024)) + " MB limit.");
        }

        String declared = normaliseContentType(file.getContentType());
        // Map.of().get(null) throws, and a multipart part with no Content-Type is
        // an ordinary thing for a client to send.
        String refusedLabel = declared == null ? null : EXPLICITLY_REFUSED.get(declared);
        if (refusedLabel != null) {
            throw new IllegalArgumentException(
                    refusedLabel + " files are not accepted: they can carry script that would run "
                    + "in the application's own origin when the file is viewed.");
        }
        if (declared == null || !ALLOWED_CONTENT_TYPES.contains(declared)) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + file.getContentType()
                    + ". Allowed types: PDF, images (JPEG/PNG/GIF/WEBP), Word, Excel, "
                    + "PowerPoint, OpenDocument, plain text and CSV.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded file bytes: " + e.getMessage(), e);
        }
        // getSize() is the client's framing; the bytes are the truth.
        if (bytes.length > maxFileSize) {
            throw new IllegalArgumentException(
                    "File size " + bytes.length + " bytes exceeds the "
                    + (maxFileSize / (1024 * 1024)) + " MB limit.");
        }

        assertContentMatchesDeclaredType(bytes, declared);

        String sanitised = sanitiseFilename(file.getOriginalFilename());

        // Last, and only on bytes that have already passed the structural
        // checks: content scanning, if a scanner has been contributed. Runs
        // before anything is written, so a rejection stores nothing.
        scanner.ifPresent(s -> s.scan(bytes, declared, sanitised));

        return new ValidatedUpload(bytes, declared, sanitised);
    }

    // ── Content sniffing ──────────────────────────────────────────────────────

    /**
     * Rejects a file whose leading bytes contradict its declared type.
     *
     * <p>Only the signatures that matter are checked. The goal is not format
     * validation, it is to stop a script-bearing document entering under a benign
     * label — so every text-ish type additionally gets the markup check, which is
     * the case that turns into stored XSS.</p>
     */
    private void assertContentMatchesDeclaredType(byte[] bytes, String declared) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("File must not be empty.");
        }

        switch (declared) {
            case "application/pdf" -> requireMagic(bytes, declared, new byte[]{'%', 'P', 'D', 'F'});
            case "image/png" -> requireMagic(bytes, declared,
                    new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/jpeg" -> requireMagic(bytes, declared,
                    new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/gif" -> {
                if (!startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    && !startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII))) {
                    throw mismatch(declared);
                }
            }
            case "image/webp" -> {
                if (bytes.length < 12
                    || !startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    || !"WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII))) {
                    throw mismatch(declared);
                }
            }
            // OOXML and OpenDocument are ZIP containers.
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                 "application/vnd.oasis.opendocument.text",
                 "application/vnd.oasis.opendocument.spreadsheet" ->
                    requireMagic(bytes, declared, new byte[]{'P', 'K', 0x03, 0x04});
            // Legacy Office is an OLE2 compound document.
            case "application/msword",
                 "application/vnd.ms-excel",
                 "application/vnd.ms-powerpoint" -> requireMagic(bytes, declared, new byte[]{
                    (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1});
            case "text/plain", "text/csv" -> requirePlainText(bytes);
            default -> { /* allow-list already bounded this */ }
        }

        // Belt and braces for every accepted type: a file that begins as markup is
        // refused whatever label it arrived under.
        assertNotMarkup(bytes);
    }

    private void requireMagic(byte[] bytes, String declared, byte[] magic) {
        if (!startsWith(bytes, magic)) {
            throw mismatch(declared);
        }
    }

    /**
     * A text upload must actually be text: valid UTF-8, no NUL bytes and no
     * other C0 control characters beyond tab/CR/LF. That rules out a binary
     * payload wearing a {@code text/csv} label.
     */
    private void requirePlainText(byte[] bytes) {
        int scanned = Math.min(bytes.length, 8192);
        for (int i = 0; i < scanned; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0x00 || (b < 0x20 && b != '\t' && b != '\n' && b != '\r')) {
                throw new IllegalArgumentException(
                        "File content does not match its declared type: it contains binary data "
                        + "but was uploaded as text.");
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, 0, scanned));
        } catch (CharacterCodingException notUtf8) {
            // A truncated multi-byte sequence at the 8 KiB boundary is not an
            // error, so only a failure well inside the window is conclusive.
            if (scanned < bytes.length) {
                return;
            }
            throw new IllegalArgumentException(
                    "File content does not match its declared type: it is not valid UTF-8 text.");
        }
    }

    private void assertNotMarkup(byte[] bytes) {
        int window = Math.min(bytes.length, 512);
        String head = new String(bytes, 0, window, StandardCharsets.UTF_8)
                .stripLeading()
                .toLowerCase(Locale.ROOT);
        for (String prefix : SCRIPTABLE_TEXT_PREFIXES) {
            if (head.startsWith(prefix)) {
                throw new IllegalArgumentException(
                        "File content is markup (HTML/XML/SVG), which is not accepted: it can carry "
                        + "script that would run in the application's own origin when viewed.");
            }
        }
    }

    private IllegalArgumentException mismatch(String declared) {
        return new IllegalArgumentException(
                "File content does not match its declared type (" + declared + "). "
                + "The file was rejected because its contents are not what its Content-Type claims.");
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    // ── Filenames ─────────────────────────────────────────────────────────────

    /**
     * Reduces a client-supplied filename to something safe to echo back and to
     * use as one segment of a storage key.
     *
     * <p>The sanitised name never determines the storage path on its own —
     * callers prefix it with a generated UUID — but it still has to survive being
     * written to a filesystem, displayed in a UI and put in a
     * {@code Content-Disposition} header without carrying a path separator, a
     * quote or a control character.</p>
     */
    public static String sanitiseFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "file";
        }
        // Take the last path segment, whichever separator the client used, so
        // "../../etc/passwd" and "C:\\evil\\x.pdf" both reduce to a leaf name.
        String name = filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace(' ', '_');
        name = name.replaceAll("[^A-Za-z0-9._\\-]", "");
        // "..", "." and "" are all path-significant; none is a filename.
        name = name.replaceAll("^\\.+", "");
        if (name.isBlank()) {
            return "file";
        }
        if (name.length() > 120) {
            // Preserve the extension, which is what a downloader keys off.
            int dot = name.lastIndexOf('.');
            String ext = (dot > 0 && name.length() - dot <= 12) ? name.substring(dot) : "";
            name = name.substring(0, 120 - ext.length()) + ext;
        }
        return name;
    }

    /**
     * Builds a storage key whose path is entirely generated. The sanitised
     * original name is appended for human legibility only and cannot, on its
     * own, place the object anywhere.
     */
    public static String buildStorageKey(String prefix, String sanitisedFilename) {
        return prefix + "/" + UUID.randomUUID() + "-" + sanitiseFilename(sanitisedFilename);
    }

    private String normaliseContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw;
        int semi = value.indexOf(';');
        if (semi >= 0) {
            value = value.substring(0, semi);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** The bytes and the metadata that survived validation. */
    public record ValidatedUpload(byte[] bytes, String contentType, String sanitisedFilename) {
    }
}
