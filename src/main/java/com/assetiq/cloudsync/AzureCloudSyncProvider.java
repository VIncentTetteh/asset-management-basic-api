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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Azure cloud asset discovery provider.
 *
 * <p>Uses the Azure Resource Manager REST API with service-principal OAuth2
 * authentication.  No Azure SDK dependency — authentication and HTTP calls are
 * handled by the same {@link java.net.http.HttpClient} used elsewhere in the stack.
 *
 * <h3>Supported resource types</h3>
 * <ul>
 *   <li>Virtual Machines → {@code VIRTUAL_MACHINE}</li>
 *   <li>Storage Accounts (Blob) → {@code STORAGE_BUCKET}</li>
 *   <li>SQL Servers / Databases → {@code DATABASE}</li>
 *   <li>AKS Managed Clusters → {@code KUBERNETES_CLUSTER}</li>
 *   <li>App Services (Web Apps + Function Apps) → {@code CONTAINER} / {@code SERVERLESS_FUNCTION}</li>
 *   <li>Load Balancers → {@code LOAD_BALANCER}</li>
 * </ul>
 *
 * <h3>Required environment variables</h3>
 * <pre>
 * AZURE_TENANT_ID          Azure AD tenant (directory) ID
 * AZURE_CLIENT_ID          Service-principal application ID
 * AZURE_CLIENT_SECRET      Service-principal client secret
 * AZURE_SUBSCRIPTION_ID    Target subscription to scan
 * </pre>
 */
@Component
public class AzureCloudSyncProvider implements CloudSyncProvider {

    private static final Logger log = LoggerFactory.getLogger(AzureCloudSyncProvider.class);
    private static final String ARM_BASE = "https://management.azure.com";
    private static final String ARM_API  = "2023-07-01";

    @Value("${app.cloud.azure.tenant-id:${AZURE_TENANT_ID:}}")
    private String tenantId;

    @Value("${app.cloud.azure.client-id:${AZURE_CLIENT_ID:}}")
    private String clientId;

    @Value("${app.cloud.azure.client-secret:${AZURE_CLIENT_SECRET:}}")
    private String clientSecret;

    @Value("${app.cloud.azure.subscription-id:${AZURE_SUBSCRIPTION_ID:}}")
    private String subscriptionId;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper om = new ObjectMapper();

    @Override
    public CloudProvider provider() { return CloudProvider.AZURE; }

    @Override
    public boolean isConfigured() {
        return !blank(tenantId) && !blank(clientId)
                && !blank(clientSecret) && !blank(subscriptionId);
    }

    @Override
    public List<CloudAsset> discover(Organisation org, List<String> regions) {
        List<CloudAsset> all = new ArrayList<>();
        try {
            String token = acquireToken();
            all.addAll(discoverVMs(org, token));
            all.addAll(discoverStorageAccounts(org, token));
            all.addAll(discoverSqlServers(org, token));
            all.addAll(discoverAks(org, token));
            all.addAll(discoverAppServices(org, token));
            all.addAll(discoverLoadBalancers(org, token));
            log.info("[Azure] Discovered {} asset(s) for org {}", all.size(), org.getId());
        } catch (Exception ex) {
            log.error("[Azure] Discovery failed for org {}: {}", org.getId(), ex.getMessage());
        }
        return all;
    }

    // ── Virtual Machines ──────────────────────────────────────────────────────

    private List<CloudAsset> discoverVMs(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = ARM_BASE + "/subscriptions/" + subscriptionId
                    + "/providers/Microsoft.Compute/virtualMachines?api-version=" + ARM_API;
            JsonNode root = getJson(url, token);
            for (JsonNode vm : root.path("value")) {
                String id       = vm.path("id").asText();
                String name     = vm.path("name").asText(id);
                String location = vm.path("location").asText("global");
                String size     = vm.at("/properties/hardwareProfile/vmSize").asText("");
                String ps       = vm.at("/properties/provisioningState").asText("").toLowerCase();
                CloudAssetStatus status = provisioningStatus(ps);
                assets.add(build(org, id, location, CloudResourceType.VIRTUAL_MACHINE,
                        name, status, null, "Azure VM · " + size));
            }
            log.info("[Azure] VMs: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[Azure] VM scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Storage Accounts ──────────────────────────────────────────────────────

    private List<CloudAsset> discoverStorageAccounts(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = ARM_BASE + "/subscriptions/" + subscriptionId
                    + "/providers/Microsoft.Storage/storageAccounts?api-version=2023-01-01";
            JsonNode root = getJson(url, token);
            for (JsonNode sa : root.path("value")) {
                String id       = sa.path("id").asText();
                String name     = sa.path("name").asText(id);
                String location = sa.path("location").asText("global");
                assets.add(build(org, id, location, CloudResourceType.STORAGE_BUCKET,
                        name, CloudAssetStatus.RUNNING, null, "Azure Storage · " + sa.path("kind").asText()));
            }
            log.info("[Azure] Storage accounts: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[Azure] Storage scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── SQL Servers ───────────────────────────────────────────────────────────

    private List<CloudAsset> discoverSqlServers(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = ARM_BASE + "/subscriptions/" + subscriptionId
                    + "/providers/Microsoft.Sql/servers?api-version=2022-05-01-preview";
            JsonNode root = getJson(url, token);
            for (JsonNode server : root.path("value")) {
                String serverId   = server.path("id").asText();
                String serverName = server.path("name").asText(serverId);
                String location   = server.path("location").asText("global");
                String state      = server.at("/properties/state").asText("").toLowerCase();
                CloudAssetStatus status = "ready".equals(state)
                        ? CloudAssetStatus.RUNNING : CloudAssetStatus.UNKNOWN;
                String desc = "Azure SQL Server · " + server.at("/properties/version").asText();
                assets.add(build(org, serverId, location, CloudResourceType.DATABASE,
                        serverName, status, null, desc));
            }
            log.info("[Azure] SQL servers: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[Azure] SQL scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── AKS ───────────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverAks(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = ARM_BASE + "/subscriptions/" + subscriptionId
                    + "/providers/Microsoft.ContainerService/managedClusters?api-version=2023-07-01";
            JsonNode root = getJson(url, token);
            for (JsonNode cluster : root.path("value")) {
                String id       = cluster.path("id").asText();
                String name     = cluster.path("name").asText(id);
                String location = cluster.path("location").asText("global");
                String ps       = cluster.at("/properties/provisioningState").asText("").toLowerCase();
                String k8sVer   = cluster.at("/properties/kubernetesVersion").asText("");
                assets.add(build(org, id, location, CloudResourceType.KUBERNETES_CLUSTER,
                        name, provisioningStatus(ps), null, "AKS k8s " + k8sVer));
            }
            log.info("[Azure] AKS clusters: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[Azure] AKS scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── App Services (Web Apps + Function Apps) ───────────────────────────────

    private List<CloudAsset> discoverAppServices(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = ARM_BASE + "/subscriptions/" + subscriptionId
                    + "/providers/Microsoft.Web/sites?api-version=2022-09-01";
            JsonNode root = getJson(url, token);
            for (JsonNode site : root.path("value")) {
                String id       = site.path("id").asText();
                String name     = site.path("name").asText(id);
                String location = site.path("location").asText("global");
                String kind     = site.path("kind").asText("").toLowerCase();
                // "functionapp" kinds → SERVERLESS_FUNCTION; everything else → CONTAINER
                CloudResourceType type = kind.contains("functionapp")
                        ? CloudResourceType.SERVERLESS_FUNCTION
                        : CloudResourceType.CONTAINER;
                String state = site.at("/properties/state").asText("").toLowerCase();
                CloudAssetStatus status = "running".equals(state)
                        ? CloudAssetStatus.RUNNING : CloudAssetStatus.STOPPED;
                assets.add(build(org, id, location, type, name, status, null,
                        "Azure App Service · " + kind));
            }
            log.info("[Azure] App services: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[Azure] App Service scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Load Balancers ────────────────────────────────────────────────────────

    private List<CloudAsset> discoverLoadBalancers(Organisation org, String token) {
        List<CloudAsset> assets = new ArrayList<>();
        try {
            String url = ARM_BASE + "/subscriptions/" + subscriptionId
                    + "/providers/Microsoft.Network/loadBalancers?api-version=2023-04-01";
            JsonNode root = getJson(url, token);
            for (JsonNode lb : root.path("value")) {
                String id       = lb.path("id").asText();
                String name     = lb.path("name").asText(id);
                String location = lb.path("location").asText("global");
                String ps       = lb.at("/properties/provisioningState").asText("").toLowerCase();
                assets.add(build(org, id, location, CloudResourceType.LOAD_BALANCER,
                        name, provisioningStatus(ps), null, "Azure Load Balancer"));
            }
            log.info("[Azure] Load balancers: {}", assets.size());
        } catch (Exception ex) {
            log.warn("[Azure] Load Balancer scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    /**
     * Obtains an Azure AD access token for the ARM audience using client-credentials flow.
     */
    private String acquireToken() throws Exception {
        String url = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";
        String body = "grant_type=client_credentials"
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&scope=" + enc("https://management.azure.com/.default");

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Azure token request failed (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        JsonNode json = om.readTree(resp.body());
        String token = json.path("access_token").asText(null);
        if (token == null) throw new IllegalStateException("No access_token in Azure response");
        return token;
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
            throw new IllegalStateException("ARM API returned HTTP " + resp.statusCode() + " for " + url);
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
        a.setProvider(CloudProvider.AZURE);
        a.setRegion(region);
        a.setResourceType(type);
        a.setName(name != null ? name : resourceId);
        a.setStatus(status);
        a.setEnvironment(environment);
        if (description != null) a.setDescription(description);
        a.setLastSyncAt(Instant.now());
        return a;
    }

    private static CloudAssetStatus provisioningStatus(String ps) {
        return switch (ps) {
            case "succeeded" -> CloudAssetStatus.RUNNING;
            case "updating",
                 "creating"  -> CloudAssetStatus.PENDING;
            case "deleting",
                 "failed"    -> CloudAssetStatus.TERMINATED;
            default          -> CloudAssetStatus.UNKNOWN;
        };
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String enc(String s)    { return URLEncoder.encode(s != null ? s : "", StandardCharsets.UTF_8); }
}
