package com.assetiq.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Upload validation")
class UploadValidatorTest {

    private final UploadValidator validator =
            new UploadValidator(UploadValidator.DEFAULT_MAX_FILE_SIZE);

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private static byte[] pdf() {
        return concat("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII), new byte[64]);
    }

    private static byte[] png() {
        return concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, new byte[32]);
    }

    private static byte[] jpeg() {
        return concat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}, new byte[32]);
    }

    private static byte[] gif() {
        return concat("GIF89a".getBytes(StandardCharsets.US_ASCII), new byte[32]);
    }

    private static byte[] webp() {
        byte[] out = new byte[64];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, out, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, out, 8, 4);
        return out;
    }

    private static byte[] ooxml() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static byte[] ole2() {
        return concat(new byte[]{(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
                                 (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1}, new byte[64]);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    // ── Accepted ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("accepts")
    class Accepts {

        @Test
        @DisplayName("every document and image type on the allow-list")
        void acceptsAllowedTypes() {
            assertThatCode(() -> {
                validator.validate(file("a.pdf", "application/pdf", pdf()));
                validator.validate(file("a.png", "image/png", png()));
                validator.validate(file("a.jpg", "image/jpeg", jpeg()));
                validator.validate(file("a.gif", "image/gif", gif()));
                validator.validate(file("a.webp", "image/webp", webp()));
                validator.validate(file("a.docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ooxml()));
                validator.validate(file("a.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ooxml()));
                validator.validate(file("a.doc", "application/msword", ole2()));
                validator.validate(file("a.xls", "application/vnd.ms-excel", ole2()));
                validator.validate(file("a.txt", "text/plain",
                        "just some notes".getBytes(StandardCharsets.UTF_8)));
                validator.validate(file("a.csv", "text/csv",
                        "tag,serial\nA-1,SN-1\n".getBytes(StandardCharsets.UTF_8)));
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a content type carrying a charset parameter")
        void acceptsContentTypeWithParameters() {
            var result = validator.validate(file("a.csv", "text/csv; charset=UTF-8",
                    "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8)));
            assertThat(result.contentType()).isEqualTo("text/csv");
        }
    }

    // ── Size ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("size limit")
    class SizeLimit {

        @Test
        @DisplayName("rejects a file over the 25 MB default")
        void rejectsOversizedFile() {
            byte[] big = concat("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII),
                                new byte[(int) UploadValidator.DEFAULT_MAX_FILE_SIZE]);

            assertThatThrownBy(() -> validator.validate(file("big.pdf", "application/pdf", big)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the 25 MB limit");
        }

        @Test
        @DisplayName("accepts a file right at the limit")
        void acceptsFileAtLimit() {
            int size = (int) UploadValidator.DEFAULT_MAX_FILE_SIZE;
            byte[] atLimit = new byte[size];
            System.arraycopy("%PDF".getBytes(StandardCharsets.US_ASCII), 0, atLimit, 0, 4);

            assertThatCode(() -> validator.validate(file("ok.pdf", "application/pdf", atLimit)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("the limit is configurable and enforced at the configured value")
        void honoursConfiguredLimit() {
            UploadValidator small = new UploadValidator(1024);

            assertThatThrownBy(() -> small.validate(
                    file("a.pdf", "application/pdf", concat("%PDF".getBytes(StandardCharsets.US_ASCII),
                                                            new byte[2048]))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds");
        }

        @Test
        @DisplayName("rejects an empty file")
        void rejectsEmptyFile() {
            assertThatThrownBy(() -> validator.validate(file("a.pdf", "application/pdf", new byte[0])))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be empty");
        }
    }

    // ── Content-type allow-list ───────────────────────────────────────────────

    @Nested
    @DisplayName("content-type allow-list")
    class ContentTypeAllowList {

        /**
         * SVG and HTML execute script in whatever origin serves them. Accepting
         * either would make every attachment a stored-XSS payload against the app.
         */
        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(strings = {
                "image/svg+xml", "text/html", "application/xhtml+xml",
                "text/xml", "application/xml"
        })
        void refusesScriptBearingTypes(String contentType) {
            byte[] body = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                    .getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> validator.validate(file("x", contentType, body)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("script");
        }

        @Test
        @DisplayName("SVG and HTML are absent from the allow-list itself")
        void allowListExcludesMarkup() {
            assertThat(UploadValidator.ALLOWED_CONTENT_TYPES)
                    .doesNotContain("image/svg+xml", "text/html", "application/xhtml+xml",
                                    "text/xml", "application/xml");
        }

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(strings = {
                "application/x-msdownload", "application/zip", "application/octet-stream",
                "application/x-sh", "application/java-archive"
        })
        void refusesTypesOutsideTheAllowList(String contentType) {
            assertThatThrownBy(() -> validator.validate(file("x", contentType, new byte[]{1, 2, 3, 4})))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
        }

        @Test
        @DisplayName("refuses a missing content type rather than guessing one")
        void refusesMissingContentType() {
            assertThatThrownBy(() -> validator.validate(file("a.pdf", null, pdf())))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported file type");
        }
    }

    // ── Content sniffing ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("content sniffing")
    class ContentSniffing {

        @Test
        @DisplayName("rejects HTML masquerading as a PDF")
        void rejectsHtmlWearingPdfLabel() {
            byte[] html = "<html><script>fetch('/api/v1/users')</script></html>"
                    .getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> validator.validate(file("invoice.pdf", "application/pdf", html)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rejects SVG masquerading as a PNG")
        void rejectsSvgWearingPngLabel() {
            byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                    .getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> validator.validate(file("logo.png", "image/png", svg)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
        }

        @Test
        @DisplayName("rejects HTML masquerading as a CSV, which passes a naive text check")
        void rejectsHtmlWearingCsvLabel() {
            byte[] html = "<!DOCTYPE html><html><body><script>alert(1)</script></body></html>"
                    .getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> validator.validate(file("report.csv", "text/csv", html)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("markup");
        }

        @ParameterizedTest(name = "{0} bytes declared as {1} are rejected")
        @CsvSource({
                "png,  application/pdf",
                "pdf,  image/png",
                "ole2, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "ooxml, application/msword",
                "jpeg, image/gif"
        })
        void rejectsCrossTypeMismatch(String actual, String declared) {
            byte[] body = switch (actual) {
                case "png" -> png();
                case "pdf" -> pdf();
                case "ole2" -> ole2();
                case "ooxml" -> ooxml();
                case "jpeg" -> jpeg();
                default -> throw new IllegalArgumentException(actual);
            };

            assertThatThrownBy(() -> validator.validate(file("x", declared, body)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
        }

        @Test
        @DisplayName("rejects a binary payload labelled text/plain")
        void rejectsBinaryWearingTextLabel() {
            byte[] binary = {0x00, 0x01, 0x02, (byte) 0xFF, (byte) 0xFE, 0x03};

            assertThatThrownBy(() -> validator.validate(file("notes.txt", "text/plain", binary)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not match its declared type");
        }

        @Test
        @DisplayName("accepts UTF-8 text with accents and newlines")
        void acceptsRealText() {
            byte[] text = "Nom,Référence\nBureau — Accra,ASSET-1\n".getBytes(StandardCharsets.UTF_8);
            assertThatCode(() -> validator.validate(file("a.csv", "text/csv", text)))
                    .doesNotThrowAnyException();
        }
    }

    // ── Filenames ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("filename sanitisation")
    class Filenames {

        @ParameterizedTest(name = "\"{0}\" becomes \"{1}\"")
        @CsvSource({
                "'../../etc/passwd',        passwd",
                "'../../../evil.pdf',       evil.pdf",
                "'/absolute/path/x.pdf',    x.pdf",
                "'C:\\\\Windows\\\\evil.doc', evil.doc",
                "'my report.pdf',           my_report.pdf",
                "'..',                      file",
                "'.',                       file",
                "'.hidden',                 hidden",
                "'x<script>.pdf',           xscript.pdf",
                "'quote\"name.pdf',         quotename.pdf"
        })
        void sanitisesHostileNames(String input, String expected) {
            assertThat(UploadValidator.sanitiseFilename(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("null and blank names fall back to a safe default")
        void handlesNullAndBlank() {
            assertThat(UploadValidator.sanitiseFilename(null)).isEqualTo("file");
            assertThat(UploadValidator.sanitiseFilename("   ")).isEqualTo("file");
            assertThat(UploadValidator.sanitiseFilename("///")).isEqualTo("file");
        }

        @Test
        @DisplayName("a very long name is truncated but keeps its extension")
        void truncatesLongNames() {
            String name = "a".repeat(400) + ".pdf";
            String sanitised = UploadValidator.sanitiseFilename(name);

            assertThat(sanitised).hasSizeLessThanOrEqualTo(120).endsWith(".pdf");
        }

        @Test
        @DisplayName("the returned filename is already sanitised, so callers cannot forget")
        void validateReturnsSanitisedName() {
            var result = validator.validate(file("../../etc/passwd.pdf", "application/pdf", pdf()));
            assertThat(result.sanitisedFilename()).isEqualTo("passwd.pdf");
        }

        @Test
        @DisplayName("the storage key is generated — the filename never determines the path")
        void storageKeyIsGenerated() {
            String keyA = UploadValidator.buildStorageKey("attachments/org-1", "../../escape.pdf");
            String keyB = UploadValidator.buildStorageKey("attachments/org-1", "../../escape.pdf");

            assertThat(keyA).startsWith("attachments/org-1/").doesNotContain("..");
            assertThat(keyA).isNotEqualTo(keyB);
        }
    }
}
