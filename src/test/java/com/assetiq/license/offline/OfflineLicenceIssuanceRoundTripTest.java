package com.assetiq.license.offline;

import com.assetiq.enums.BillingPlanTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Round-trips the vendor tooling against the verifier.
 *
 * <p>{@code scripts/licence/issue-offline-licence.sh} builds the JWS by hand with
 * {@code openssl} and base64url-encodes it with {@code tr}, while the backend
 * verifies it with JJWT. Those are entirely separate implementations of the same
 * spec, and the failure mode if they disagree — on base64url padding, on the
 * exact signing input, on the key encoding — is that every licence the vendor
 * issues silently resolves to the free tier. The unit tests cannot catch that,
 * because they sign with JJWT too.</p>
 *
 * <p>Skipped on Windows and wherever {@code openssl} is unavailable.</p>
 */
@DisabledOnOs(OS.WINDOWS)
class OfflineLicenceIssuanceRoundTripTest {

    private static final Path SCRIPT =
            Paths.get("scripts", "licence", "issue-offline-licence.sh").toAbsolutePath();

    @Test
    @DisplayName("a key issued by the signing script verifies and grants its tier")
    void issuedKeyVerifies(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isReadable(SCRIPT), "signing script not present");
        assumeTrue(commandExists("openssl"), "openssl not available");

        Path keys = tmp.resolve("keys");
        run(tmp, SCRIPT.toString(), "--generate-keypair", keys.toString());

        Path privateKey = keys.resolve("offline-private.pem");
        Path publicKey = keys.resolve("offline-public.pem");
        assertThat(privateKey).exists();
        assertThat(publicKey).exists();

        // The private key is the root of trust for every licence ever issued;
        // the script must not leave it group- or world-readable.
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(privateKey);
        assertThat(perms).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

        String expiry = LocalDate.now().plusYears(1).toString();
        String output = run(tmp, SCRIPT.toString(),
                "--key", privateKey.toString(),
                "--org", "Acme Bank Ltd",
                "--plan", "BUSINESS",
                "--seats", "25",
                "--expires", expiry);

        String token = output.lines()
                .map(String::trim)
                .filter(l -> l.chars().filter(c -> c == '.').count() == 2 && !l.contains(" "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("script printed no licence key:\n" + output));

        OfflineLicenseProperties props = new OfflineLicenseProperties();
        props.setEnabled(true);
        props.setKey(token);
        props.setPublicKey(Files.readString(publicKey));

        OfflineLicenseService service = new OfflineLicenseService(props, Clock.systemUTC());
        service.init();

        OfflineLicense licence = service.current();
        assertThat(licence.status()).isEqualTo(OfflineLicenseStatus.VALID);
        assertThat(licence.tier()).isEqualTo(BillingPlanTier.BUSINESS);
        assertThat(licence.organisation()).isEqualTo("Acme Bank Ltd");
        assertThat(licence.seats()).isEqualTo(25);
    }

    @Test
    @DisplayName("a key issued under one keypair does not verify under another")
    void keysAreBoundToTheirIssuer(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.isReadable(SCRIPT), "signing script not present");
        assumeTrue(commandExists("openssl"), "openssl not available");

        Path vendor = tmp.resolve("vendor");
        Path forger = tmp.resolve("forger");
        run(tmp, SCRIPT.toString(), "--generate-keypair", vendor.toString());
        run(tmp, SCRIPT.toString(), "--generate-keypair", forger.toString());

        String forged = run(tmp, SCRIPT.toString(),
                "--key", forger.resolve("offline-private.pem").toString(),
                "--org", "Acme Bank Ltd",
                "--plan", "ENTERPRISE",
                "--expires", LocalDate.now().plusYears(1).toString())
                .lines().map(String::trim)
                .filter(l -> l.chars().filter(c -> c == '.').count() == 2 && !l.contains(" "))
                .findFirst().orElseThrow();

        OfflineLicenseProperties props = new OfflineLicenseProperties();
        props.setEnabled(true);
        props.setKey(forged);
        props.setPublicKey(Files.readString(vendor.resolve("offline-public.pem")));

        OfflineLicenseService service = new OfflineLicenseService(props, Clock.systemUTC());
        service.init();

        assertThat(service.current().status()).isEqualTo(OfflineLicenseStatus.INVALID_SIGNATURE);
        assertThat(service.currentTier()).isEqualTo(BillingPlanTier.FREEMIUM);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String run(Path cwd, String... command) throws Exception {
        Process p = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).as("script timed out").isTrue();
        assertThat(p.exitValue()).as("script failed:%n%s", output).isZero();
        return output;
    }

    private static boolean commandExists(String command) {
        try {
            return new ProcessBuilder("which", command).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
