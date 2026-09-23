package com.assetiq.storage;

/**
 * The seam where malware scanning goes.
 *
 * <p>AssetIQ does <strong>not</strong> scan uploaded files for malware today.
 * {@link UploadValidator} bounds what a file can be <em>interpreted</em> as —
 * its size, its type, and whether its bytes match the type it claims — which is
 * what stops an upload becoming script in AssetIQ's own origin. It says nothing
 * about what the file contains. A PDF with a real {@code %PDF} header and a
 * malicious payload inside passes every check in this package.</p>
 *
 * <p>This interface exists so that adding a scanner is a matter of contributing
 * one bean rather than reworking the upload path. Every entry point that accepts
 * a file goes through {@code UploadValidator}, which calls the scanner after the
 * cheap structural checks and before the bytes reach storage — so a scanner
 * implemented here is enforced everywhere, and a rejection means nothing was
 * ever written.</p>
 *
 * <p>An implementation should be a {@code @Component} (or {@code @Bean}); the
 * single one on the context is used, and with none the upload path is unscanned.
 * A deployment with a regulatory obligation to scan uploads should put a
 * scanning proxy or gateway in front of AssetIQ until a real implementation
 * ships here.</p>
 */
public interface FileContentScanner {

    /**
     * Inspects the bytes about to be stored.
     *
     * @param bytes       the complete file content
     * @param contentType the content type that has already been verified against
     *                    the file's leading bytes
     * @param filename    the sanitised filename, for logging and reporting
     * @throws IllegalArgumentException if the file must not be stored. The
     *         message is surfaced to the uploader, so it should say that the file
     *         was rejected without detailing the signature that matched.
     */
    void scan(byte[] bytes, String contentType, String filename);
}
