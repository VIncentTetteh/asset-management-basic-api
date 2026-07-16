package com.assetiq.cloudsync;

import com.assetiq.enums.CloudAssetStatus;
import com.assetiq.enums.CloudProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GcpCloudSyncProvider} covering:
 * <ul>
 *   <li>{@link GcpCloudSyncProvider#isConfigured()} credential-check logic</li>
 *   <li>{@link GcpCloudSyncProvider#provider()} identity</li>
 *   <li>Private {@code gcpInstanceStatus()} status-mapping (via reflection)</li>
 *   <li>Private {@code zoneToRegion()} zone-stripping helper (via reflection)</li>
 * </ul>
 *
 * <p>HTTP calls are not tested here — those require a mock HTTP server and are
 * considered an integration concern.
 */
@DisplayName("GcpCloudSyncProvider")
class GcpCloudSyncProviderTest {

    GcpCloudSyncProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GcpCloudSyncProvider();
        // Default: no credentials set
        setField("projectId", "");
        setField("serviceAccountKeyB64", "");
        setField("credentialsFilePath", "");
    }

    // ── provider() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("provider() returns GCP")
    void providerIsGcp() {
        assertThat(provider.provider()).isEqualTo(CloudProvider.GCP);
    }

    // ── isConfigured() ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isConfigured()")
    class IsConfigured {

        @Test
        @DisplayName("false when projectId is blank even if key is present")
        void falseWhenProjectIdBlank() {
            setField("projectId", "");
            setField("serviceAccountKeyB64", "some-key");

            assertThat(provider.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("false when projectId is set but both key sources are blank")
        void falseWhenBothKeySourcesBlank() {
            setField("projectId", "my-project");
            setField("serviceAccountKeyB64", "");
            setField("credentialsFilePath", "");

            assertThat(provider.isConfigured()).isFalse();
        }

        @Test
        @DisplayName("true when projectId and serviceAccountKeyB64 are both set")
        void trueWhenProjectAndKeyB64Present() {
            setField("projectId", "my-project");
            setField("serviceAccountKeyB64", "base64encodedkey");

            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("true when projectId and credentialsFilePath are both set")
        void trueWhenProjectAndCredentialsFilePresent() {
            setField("projectId", "my-project");
            setField("credentialsFilePath", "/var/secrets/sa-key.json");

            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("true when all three fields are set — serviceAccountKeyB64 takes precedence (no crash)")
        void trueWhenAllFieldsSet() {
            setField("projectId", "my-project");
            setField("serviceAccountKeyB64", "key");
            setField("credentialsFilePath", "/path/to/file.json");

            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("false when projectId is whitespace only")
        void falseWhenProjectIdWhitespace() {
            setField("projectId", "   ");
            setField("serviceAccountKeyB64", "key");

            assertThat(provider.isConfigured()).isFalse();
        }
    }

    // ── GCE instance status mapping ───────────────────────────────────────────

    @Nested
    @DisplayName("gcpInstanceStatus()")
    class GcpInstanceStatus {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "RUNNING,       RUNNING",
                "TERMINATED,    STOPPED",
                "SUSPENDED,     STOPPED",
                "STAGING,       PENDING",
                "PROVISIONING,  PENDING",
                "STOPPING,      TERMINATED",
                "DEPROVISIONING,TERMINATED",
                "UNKNOWN_STATE, UNKNOWN",
                "'',            UNKNOWN"
        })
        @DisplayName("maps GCE instance state to CloudAssetStatus")
        void mapsGceState(String gcpState, CloudAssetStatus expected) throws Exception {
            var method = GcpCloudSyncProvider.class
                    .getDeclaredMethod("gcpInstanceStatus", String.class);
            method.setAccessible(true);

            CloudAssetStatus result = (CloudAssetStatus) method.invoke(null, gcpState);
            assertThat(result).isEqualTo(expected);
        }
    }

    // ── Zone-to-region helper ─────────────────────────────────────────────────

    @Nested
    @DisplayName("zoneToRegion()")
    class ZoneToRegion {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({
                "us-central1-a,   us-central1",
                "europe-west1-b,  europe-west1",
                "asia-east1-c,    asia-east1",
                "us-central1,     us-central1",   // already a region
                "global,          global",
                "'',              global",
        })
        @DisplayName("strips zone suffix to produce region")
        void stripsZoneSuffix(String zone, String expectedRegion) throws Exception {
            var method = GcpCloudSyncProvider.class
                    .getDeclaredMethod("zoneToRegion", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(null, zone);
            assertThat(result).isEqualTo(expectedRegion);
        }

        @Test
        @DisplayName("null zone returns 'global'")
        void nullZone_returnsGlobal() throws Exception {
            var method = GcpCloudSyncProvider.class
                    .getDeclaredMethod("zoneToRegion", String.class);
            method.setAccessible(true);

            String result = (String) method.invoke(null, (Object) null);
            assertThat(result).isEqualTo("global");
        }
    }

    // ── matchesRegion helper ──────────────────────────────────────────────────

    @Nested
    @DisplayName("matchesRegion()")
    class MatchesRegion {

        @Test
        @DisplayName("true when region starts with a filter entry")
        void trueWhenPrefixMatch() throws Exception {
            var method = GcpCloudSyncProvider.class
                    .getDeclaredMethod("matchesRegion", String.class, java.util.List.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(null,
                    "us-central1", java.util.List.of("us-central1"));
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("true when filter entry starts with region (broader filter)")
        void trueWhenReversePrefix() throws Exception {
            var method = GcpCloudSyncProvider.class
                    .getDeclaredMethod("matchesRegion", String.class, java.util.List.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(null,
                    "us", java.util.List.of("us-central1"));
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("false when no filter entry matches")
        void falseWhenNoMatch() throws Exception {
            var method = GcpCloudSyncProvider.class
                    .getDeclaredMethod("matchesRegion", String.class, java.util.List.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(null,
                    "asia-east1", java.util.List.of("us-central1", "europe-west1"));
            assertThat(result).isFalse();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setField(String fieldName, String value) {
        ReflectionTestUtils.setField(provider, fieldName, value);
    }
}
