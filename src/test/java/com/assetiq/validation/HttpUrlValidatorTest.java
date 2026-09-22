package com.assetiq.validation;

import com.assetiq.dto.ContractDto;
import com.assetiq.dto.DisposalRecordDto;
import com.assetiq.dto.ExpenseDto;
import com.assetiq.dto.SoftwareLicenseDto;
import com.assetiq.dto.compliance.BogControlDto;
import com.assetiq.dto.compliance.ComplianceControlDto;
import com.assetiq.dto.compliance.PciSaqRecordDto;
import com.assetiq.dto.compliance.SecurityPolicyDto;
import com.assetiq.dto.compliance.VulnerabilityScanDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Link fields accept only absolute http(s) URLs, so no client can store a javascript: link. */
class HttpUrlValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static boolean contractUrlValid(String url) {
        ContractDto dto = new ContractDto();
        dto.setDocumentUrl(url);
        return validator.validateProperty(dto, "documentUrl").isEmpty();
    }

    private static boolean disposalDocValid(String value) {
        DisposalRecordDto dto = new DisposalRecordDto();
        dto.setComplianceDocumentUrl(value);
        return validator.validateProperty(dto, "complianceDocumentUrl").isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)", "JAVASCRIPT:alert(1)", " javascript:alert(1)", "\u0001javascript:alert(1)",
            "java\tscript:alert(1)", "data:text/html,<script>alert(1)</script>", "vbscript:msgbox(1)",
            "/relative/path", "relative.pdf", "//evil.example/x", "mailto:a@b.c", "ftp://files.example/x",
            "https://", "https:///nohost", "http://exa mple.com"})
    void rejectsAnythingButAnAbsoluteHttpUrl(String url) {
        assertThat(contractUrlValid(url)).as(url).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://docs.example.com/c.pdf", "HTTP://example.com/a?b=c#d", "https://example.com:8443/x"})
    void acceptsHttpAndHttps(String url) {
        assertThat(contractUrlValid(url)).as(url).isTrue();
    }

    @Test
    void nullAndBlankAreAllowed() {
        assertThat(contractUrlValid(null)).isTrue();
        assertThat(contractUrlValid("")).isTrue();
        assertThat(contractUrlValid("   ")).isTrue();
    }

    @Test
    void disposalDocumentMayBeAReferenceButNeverAScriptUrl() {
        assertThat(disposalDocValid("Certificate of destruction #12345")).isTrue();
        assertThat(disposalDocValid("Ref: 12345")).isTrue();
        assertThat(disposalDocValid("https://docs.example.com/cert.pdf")).isTrue();
        assertThat(disposalDocValid("javascript:alert(1)")).isFalse();
        assertThat(disposalDocValid(" \tJaVa\nScript:alert(1)")).isFalse();
        assertThat(disposalDocValid("data:text/html,x")).isFalse();
    }

    @Test
    void everyRenderedLinkFieldCarriesTheConstraint() {
        List<String> missing = List.of(
                        field(ExpenseDto.class, "receiptUrl"), field(SoftwareLicenseDto.class, "licenseDocumentUrl"),
                        field(ContractDto.class, "documentUrl"), field(DisposalRecordDto.class, "complianceDocumentUrl"),
                        field(ComplianceControlDto.class, "evidenceUrl"), field(VulnerabilityScanDto.class, "reportUrl"),
                        field(BogControlDto.class, "evidenceUrl"), field(PciSaqRecordDto.class, "evidenceUrl"),
                        field(SecurityPolicyDto.class, "documentUrl"))
                .stream()
                .filter(f -> !f.isAnnotationPresent(HttpUrl.class))
                .map(f -> f.getDeclaringClass().getSimpleName() + "." + f.getName())
                .collect(Collectors.toList());
        assertThat(missing).isEmpty();
    }

    private static Field field(Class<?> type, String name) {
        try {
            return type.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }
}
