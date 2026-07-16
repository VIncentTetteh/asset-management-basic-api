package com.assetiq.cloudsync;

import com.assetiq.enums.CloudAssetStatus;
import com.assetiq.enums.CloudProvider;
import com.assetiq.enums.CloudResourceType;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.Organisation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * GCP cloud asset discovery provider.
 *
 * <p>Uses the Google Cloud REST APIs with service-account JWT authentication
 * (RFC 7523 / Google OAuth2).  No GCP SDK dependency — the same
 * {@link java.net.http.HttpClient} used by the Azure provider handles all HTTP
 * calls; RSA signing is done with the JDK's built-in {@code java.security} APIs.
 *
 * <h3>Supported resource types</h3>
 * <ul>
 *   <li>Compute Engine VM instances  → {@code VIRTUAL_MACHINE}</li>
 *   <li>Cloud Storage buckets        → {@code STORAGE_BUCKET}</li>
 *   <li>Cloud SQL instances          → {@code DATABASE}</li>
 *   <li>GKE clusters                 → {@code KUBERNETES_CLUSTER}</li>
 *   <li>Cloud Functions (gen 1 + 2)  → {@code SERVERLESS_FUNCTION}</li>
 *   <li>Cloud Load Balancing (URL maps as proxy for backend services) → {@code LOAD_BALANCER}</li>
 * </ul>
 *
 * <h3>Required environment / application properties</h3>
 * <pre>
 * GCP_PROJECT_ID             GCP project to scan
 * GCP_SERVICE_ACCOUNT_KEY    Base64-encoded service-account JSON key file
 *                            (generate with: base64 -w 0 sa-key.json)
 *                            OR set GOOGLE_APPLICATION_CREDENTIALS to a file path
 *                            and leave GCP_SERVICE_ACCOUNT_KEY blank — the provider
 *                            will read the file directly.
 * </pre>
 */
@Component
public class GcpCloudSyncProvider implements CloudSyncProvider {

    private static final Logger log = LoggerFactory.getLogger(GcpCloudSyncProvider.class);

    private static final String COMPUTE_BASE  = "https://compute.googleapis.com/compute/v1";
    private static final String STORAGE_BASE  = "https://storage.googleapis.com/storage/v1";
    private static final String SQL_BASE      = "https://sqladmin.googleapis.com/v1";
    private static final String GKE_BASE      = "https://container.googleapis.com/v1";
    private static final String CF1_BASE      = "https://cloudfunctions.googleapis.com/v1";
    private static final String CF2_BASE      = "https://cloudfunctions.googleapis.com/v2";
    private static final String TOKEN_URL     = "https://oauth2.googleapis.com/token";
    private static final String SCOPE         = "https://www.googleapis.com/auth/cloud-platform.read-only";

    @Value("${app.cloud.gcp.project-id:${GCP_PROJECT_ID:}}")
    private String projectId;

    /**
     * Base64-encoded service-account JSON key.  Mutually exclusive with
     * {@code app.cloud.gcp.credentials-file}.
     */
    @Value("${app.cloud.gcp.service-account-key:${GCP_SERVICE_ACCOUNT_KEY:}}")
    private String serviceAccountKeyB64;

    /**
     * Path to a service-account JSON key file on disk.  Used only when
     * {@code serviceAccountKeyB64} is blank.
     */
    @Value("${app.cloud.gcp.credentials-file:${GOOGLE_APPLICATION_CREDENTIALS:}}")
    private String credentialsFilePath;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper om = new ObjectMapper();

    @Override
    public CloudProvider provider() { return CloudProvider.GCP; }

    @Override
    public boolean isConfigured() {
        if (blank(projectId)) return false;
        return !blank(serviceAccountKeyB64) || !blank(credentialsFilePath);
    }

    @Override
    public List<CloudAsset> discover(Organisation org, List<String> regions) {
        List<CloudAsset> all = new ArrayList<>();
        try {
            String saJson = resolveServiceAccountJson();
            if (saJson == null) {
                log.warn("[GCP] No service account key available — skipping discovery for org {}", org.getId());
                return all;
            }
            String token = acquireToken(saJson);
            all.addAll(discoverVMs(org, token, regions));
            all.addAll(discoverStorageBuckets(org, token));
            all.addAll(discoverCloudSql(org, token));
            all.addAll(discoverGke(org, token, regions));
            all.addAll(discoverCloudFunctions(org, token, regions));
            all.addAll(discoverLoadBalancers(org, token));
            log.info("[GCP] Discovered {} asset(s) for org {}", all.size(), org.getId());
        } catch (Exception ex) {
            log.error("[GCP] Discovery failed for org {}: {}", org.getId(), ex.getMessage());
        }
        return all;
    }

    // ── Compute Engine VMs ────────────────────────────────────────────────────

    private List<CloudAsset> discoverVMs(Organisation org, String token, List<String> regions) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            // aggregatedList returns instances across all zones in a single call
            String url = COMPUTE_BASE + "/projects/" + enc(projectId) + "/aggregated/instances?maxResults=500";
            JsonNode root = getJson(url, token);
            JsonNode items = root.path("items");
            items.fieldNames().forEachRemaining(zoneName -> {
                JsonNode zoneData = items.path(zoneName);
                for (JsonNode vm : zoneData.path("instances")) {
                    String id       = vm.path("id").asText();
                    String name     = vm.path("name").asText(id);
                    String selfLink = vm.path("selfLink").asText("");
                    String zone     = zoneName.replaceFirst("zones/", "");
                    String region   = zoneToRegion(zone);
                    if (!regions.isEmpty() && !matchesRegion(region, regions)) continue;
                    String status   = vm.path("status").asText("").toUpperCase();
                    String machineType = vm.path("machineType").asText("").replaceAll(".*/machineTypes/", "");
                    assets.add(build(org, selfLink.isBlank() ? id : selfLink, region,
                            CloudResourceType.VIRTUAL_MACHINE, name,
                            gcpInstanceStatus(status), null, "GCE · " + machineType));
                }
            });
            log.info("[GCP] Compute VMs: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[GCP] VM scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Cloud Storage ─────────────────────────────────────────────────────────

    private List<CloudAsset> discoverStorageBuckets(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = STORAGE_BASE + "/b?project=" + enc(projectId) + "&maxResults=500";
            JsonNode root = getJson(url, token);
            for (JsonNode bucket : root.path("items")) {
                String id       = bucket.path("id").asText();
                String name     = bucket.path("name").asText(id);
                String location = bucket.path("location").asText("global").toLowerCase();
                String selfLink = bucket.path("selfLink").asText(id);
                assets.add(build(org, selfLink, location,
                        CloudResourceType.STORAGE_BUCKET, name,
                        CloudAssetStatus.RUNNING, null,
                        "GCS · " + bucket.path("storageClass").asText()));
            }
            log.info("[GCP] Cloud Storage buckets: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[GCP] Storage scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Cloud SQL ─────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverCloudSql(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = SQL_BASE + "/projects/" + enc(projectId) + "/instances?maxResults=500";
            JsonNode root = getJson(url, token);
            for (JsonNode inst : root.path("items")) {
                String name     = inst.path("name").asText();
                String selfLink = inst.path("selfLink").asText(name);
                String region   = inst.path("region").asText("global");
                String state    = inst.path("state").asText("").toUpperCase();
                String dbVer    = inst.path("databaseVersion").asText("");
                CloudAssetStatus status = "RUNNABLE".equals(state) ? CloudAssetStatus.RUNNING
                        : "SUSPENDED".equals(state) ? CloudAssetStatus.STOPPED
                        : CloudAssetStatus.UNKNOWN;
                assets.add(build(org, selfLink, region,
                        CloudResourceType.DATABASE, name, status, null, "Cloud SQL · " + dbVer));
            }
            log.info("[GCP] Cloud SQL instances: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[GCP] Cloud SQL scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── GKE ───────────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverGke(Organisation org, String token, List<String> regions) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            // "-" as zone/region = all zones for the project
            String url = GKE_BASE + "/projects/" + enc(projectId) + "/locations/-/clusters?pageSize=500";
            JsonNode root = getJson(url, token);
            for (JsonNode cluster : root.path("clusters")) {
                String name     = cluster.path("name").asText();
                String selfLink = cluster.path("selfLink").asText(name);
                String location = cluster.path("location").asText("global");
                String region   = zoneToRegion(location);
                if (!regions.isEmpty() && !matchesRegion(region, regions)) continue;
                String status   = cluster.path("status").asText("").toUpperCase();
                String k8sVer   = cluster.path("currentMasterVersion").asText("");
                CloudAssetStatus cs = "RUNNING".equals(status) ? CloudAssetStatus.RUNNING
                        : "PROVISIONING".equals(status) ? CloudAssetStatus.PENDING
                        : "STOPPING".equals(status) ? CloudAssetStatus.TERMINATED
                        : CloudAssetStatus.UNKNOWN;
                assets.add(build(org, selfLink, region,
                        CloudResourceType.KUBERNETES_CLUSTER, name, cs, null, "GKE k8s " + k8sVer));
            }
            log.info("[GCP] GKE clusters: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[GCP] GKE scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Cloud Functions (gen 1 + gen 2) ──────────────────────────────────────

    private List<CloudAsset> discoverCloudFunctions(Organisation org, String token, List<String> regions) {
        List<CloudAsset> assets = new ArrayList<>();
        // Gen 1 — projects/{project}/locations/{region}/functions
        assets.addAll(discoverCf(org, token, regions, CF1_BASE, "v1", "functions"));
        // Gen 2 — same path structure, different base URL
        assets.addAll(discoverCf(org, token, regions, CF2_BASE, "v2", "functions"));
        log.info("[GCP] Cloud Functions total: {}", assets.size());
        return assets;
    }

    private List<CloudAsset> discoverCf(Organisation org, String token,
                                        List<String> regions, String base,
                                        String genLabel, String collectionField) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            // "-" for location = all regions
            String url = base + "/projects/" + enc(projectId) + "/locations/-/" + collectionField + "?pageSize=500";
            JsonNode root = getJson(url, token);
            for (JsonNode fn : root.path(collectionField)) {
                String name     = fn.path("name").asText();
                // name = projects/{p}/locations/{loc}/functions/{fnName}
                String fnName   = name.contains("/") ? name.substring(name.lastIndexOf('/') + 1) : name;
                String location = extractPart(name, "locations");
                String region   = zoneToRegion(location);
                if (!regions.isEmpty() && !matchesRegion(region, regions)) continue;
                String state    = fn.path("state").asText(fn.path("status").asText("")).toUpperCase();
                CloudAssetStatus status = "ACTIVE".equals(state) ? CloudAssetStatus.RUNNING
                        : "OFFLINE".equals(state) ? CloudAssetStatus.STOPPED
                        : "DEPLOY_IN_PROGRESS".equals(state) || "DEPLOYING".equals(state) ? CloudAssetStatus.PENDING
                        : CloudAssetStatus.UNKNOWN;
                assets.add(build(org, name, region,
                        CloudResourceType.SERVERLESS_FUNCTION, fnName, status, null,
                        "Cloud Function " + genLabel));
            }
        } catch (Exception ex) {
            log.warn("[GCP] Cloud Functions ({}) scan failed: {}", genLabel, ex.getMessage());
        }
        return assets;
    }

    // ── Cloud Load Balancing (forwarding rules as top-level LB proxies) ───────

    private List<CloudAsset> discoverLoadBalancers(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            // Global forwarding rules represent the front end of every HTTP(S)/TCP/SSL LB
            String url = COMPUTE_BASE + "/projects/" + enc(projectId) + "/global/forwardingRules?maxResults=500";
            JsonNode root = getJson(url, token);
            for (JsonNode rule : root.path("items")) {
                String id       = rule.path("id").asText();
                String name     = rule.path("name").asText(id);
                String selfLink = rule.path("selfLink").asText(id);
                String lbScheme = rule.path("loadBalancingScheme").asText("");
                assets.add(build(org, selfLink, "global",
                        CloudResourceType.LOAD_BALANCER, name,
                        CloudAssetStatus.RUNNING, null,
                        "GCP Load Balancer · " + lbScheme));
            }
            log.info("[GCP] Load balancers (global forwarding rules): {}", assets.size());
        } catch (Exception ex) {
            log.warn("[GCP] Load Balancer scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Exchanges a service-account JSON key for a short-lived OAuth2 access token
     * using the Google JWT Bearer flow (RFC 7523).
     *
     * <p>Steps:
     * <ol>
     *   <li>Parse the service-account JSON to extract {@code client_email} and
     *       {@code private_key}.</li>
     *   <li>Build a JWT: header + claims signed with RS256.</li>
     *   <li>POST the JWT to the Google token endpoint and return the
     *       {@code access_token}.</li>
     * </ol>
     */
    private String acquireToken(String saJson) throws Exception {
        JsonNode sa = om.readTree(saJson);
        String clientEmail = sa.path("client_email").asText(null);
        String privateKeyPem = sa.path("private_key").asText(null);
        if (blank(clientEmail) || blank(privateKeyPem)) {
            throw new IllegalStateException("Service account JSON is missing client_email or private_key");
        }

        PrivateKey privateKey = parsePkcs8PrivateKey(privateKeyPem);
        long now = Instant.now().getEpochSecond();

        // Build JWT header + claims
        String header = b64url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claims = b64url("{" +
                "\"iss\":\"" + esc(clientEmail) + "\"," +
                "\"scope\":\"" + SCOPE + "\"," +
                "\"aud\":\"" + TOKEN_URL + "\"," +
                "\"iat\":" + now + "," +
                "\"exp\":" + (now + 3600) +
                "}");

        String signingInput = header + "." + claims;
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sig.sign());

        String jwt = signingInput + "." + signature;

        // Exchange JWT for access token
        String body = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&assertion=" + enc(jwt);

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("GCP token request failed (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        JsonNode json = om.readTree(resp.body());
        String token = json.path("access_token").asText(null);
        if (token == null) throw new IllegalStateException("No access_token in GCP response");
        return token;
    }

    /** Reads / decodes the service account JSON from config. Returns null if not available. */
    private String resolveServiceAccountJson() {
        if (!blank(serviceAccountKeyB64)) {
            try {
                return new String(Base64.getDecoder().decode(serviceAccountKeyB64), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                // Might not be base64 — treat as raw JSON
                return serviceAccountKeyB64;
            }
        }
        if (!blank(credentialsFilePath)) {
            try {
                return java.nio.file.Files.readString(java.nio.file.Path.of(credentialsFilePath));
            } catch (Exception ex) {
                log.warn("[GCP] Cannot read credentials file '{}': {}", credentialsFilePath, ex.getMessage());
            }
        }
        return null;
    }

    // ── HTTP helper ───────────────────────────────────────────────────────────

    private JsonNode getJson(String url, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("GCP API returned HTTP " + resp.statusCode() + " for " + url);
        }
        return om.readTree(resp.body());
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private CloudAsset build(Organisation org, String resourceId, String region,
                             CloudResourceType type, String name,
                             CloudAssetStatus status, String environment, String description) {
        CloudAsset a = new CloudAsset();
        a.setOrganisation(org);
        a.setResourceId(resourceId);
        a.setProvider(CloudProvider.GCP);
        a.setRegion(region);
        a.setResourceType(type);
        a.setName(name != null ? name : resourceId);
        a.setStatus(status);
        a.setEnvironment(environment);
        if (description != null) a.setDescription(description);
        a.setLastSyncAt(Instant.now());
        return a;
    }

    private static CloudAssetStatus gcpInstanceStatus(String status) {
        return switch (status) {
            case "RUNNING"      -> CloudAssetStatus.RUNNING;
            case "TERMINATED"   -> CloudAssetStatus.STOPPED;   // GCP: TERMINATED = powered off
            case "SUSPENDED"    -> CloudAssetStatus.STOPPED;
            case "STAGING",
                 "PROVISIONING" -> CloudAssetStatus.PENDING;
            case "STOPPING",
                 "DEPROVISIONING" -> CloudAssetStatus.TERMINATED;
            default             -> CloudAssetStatus.UNKNOWN;
        };
    }

    /** "us-central1-a" → "us-central1"; "us-central1" → "us-central1". */
    private static String zoneToRegion(String zone) {
        if (zone == null || zone.isBlank()) return "global";
        int last = zone.lastIndexOf('-');
        String suffix = last >= 0 ? zone.substring(last + 1) : "";
        if (last > 0 && suffix.length() == 1 && Character.isLetter(suffix.charAt(0))) {
            return zone.substring(0, last);
        }
        return zone;
    }

    /** True if {@code region} starts with any element of {@code regions} (prefix match). */
    private static boolean matchesRegion(String region, List<String> regions) {
        for (String r : regions) {
            if (region.startsWith(r) || r.startsWith(region)) return true;
        }
        return false;
    }

    /** Extracts the segment after {@code key} in a GCP resource name. */
    private static String extractPart(String name, String key) {
        String[] parts = name.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (key.equals(parts[i])) return parts[i + 1];
        }
        return "global";
    }

    private static PrivateKey parsePkcs8PrivateKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(stripped);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String b64url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String enc(String s)    { return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8); }
    private static String esc(String s)    { return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""); }
}
