package com.assetiq.cloudsync;

import com.assetiq.enums.CloudAssetStatus;
import com.assetiq.enums.CloudProvider;
import com.assetiq.enums.CloudResourceType;
import com.assetiq.models.CloudAsset;
import com.assetiq.models.Organisation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Cluster;
import software.amazon.awssdk.services.eks.EksClient;
import software.amazon.awssdk.services.elasticache.ElastiCacheClient;
import software.amazon.awssdk.services.elasticache.model.CacheCluster;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.FunctionConfiguration;
import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DBInstance;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AWS cloud asset discovery provider.
 *
 * <p>Discovers all major AWS resource types across the specified regions using the
 * AWS SDK v2 with {@link DefaultCredentialsProvider} (IAM role → env vars →
 * {@code ~/.aws/credentials} chain).  Each service type is scanned in isolation so
 * a single IAM permission gap does not abort the entire scan.
 *
 * <h3>Supported resource types</h3>
 * <ul>
 *   <li>EC2 instances → {@code VIRTUAL_MACHINE}</li>
 *   <li>RDS instances → {@code DATABASE}</li>
 *   <li>ElastiCache clusters → {@code CACHE}</li>
 *   <li>Lambda functions → {@code SERVERLESS_FUNCTION}</li>
 *   <li>ALB / NLB load balancers → {@code LOAD_BALANCER}</li>
 *   <li>ECS clusters → {@code CONTAINER}</li>
 *   <li>EKS clusters → {@code KUBERNETES_CLUSTER}</li>
 *   <li>S3 buckets (global) → {@code STORAGE_BUCKET}</li>
 * </ul>
 *
 * <h3>Credentials</h3>
 * No extra configuration required when running on EC2/ECS with an attached IAM role.
 * For cross-account or CI use, set {@code AWS_ACCESS_KEY_ID} / {@code AWS_SECRET_ACCESS_KEY}
 * / {@code AWS_SESSION_TOKEN} environment variables.
 */
@Component
public class AwsCloudSyncProvider implements CloudSyncProvider {

    private static final Logger log = LoggerFactory.getLogger(AwsCloudSyncProvider.class);

    @Value("${app.storage.s3.region:us-east-1}")
    private String defaultRegion;

    @Override
    public CloudProvider provider() {
        return CloudProvider.AWS;
    }

    @Override
    public boolean isConfigured() {
        try {
            DefaultCredentialsProvider.create().resolveCredentials();
            return true;
        } catch (Exception ex) {
            log.debug("[AWS] No credentials available: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public List<CloudAsset> discover(Organisation org, List<String> regions) {
        List<String> effectiveRegions = (regions != null && !regions.isEmpty())
                ? regions : List.of(defaultRegion);

        List<CloudAsset> all = new ArrayList<>();

        for (String regionCode : effectiveRegions) {
            Region awsRegion;
            try {
                awsRegion = Region.of(regionCode);
            } catch (IllegalArgumentException ex) {
                log.warn("[AWS] Unknown region '{}' — skipping", regionCode);
                continue;
            }

            all.addAll(discoverEc2(org, awsRegion, regionCode));
            all.addAll(discoverRds(org, awsRegion, regionCode));
            all.addAll(discoverElastiCache(org, awsRegion, regionCode));
            all.addAll(discoverLambda(org, awsRegion, regionCode));
            all.addAll(discoverElb(org, awsRegion, regionCode));
            all.addAll(discoverEcs(org, awsRegion, regionCode));
            all.addAll(discoverEks(org, awsRegion, regionCode));
        }

        // S3 is global — scan once using the first region as the endpoint region
        all.addAll(discoverS3(org, effectiveRegions.get(0)));

        log.info("[AWS] Discovered {} asset(s) across {} region(s) for org {}",
                all.size(), effectiveRegions.size(), org.getId());
        return all;
    }

    // ── EC2 ───────────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverEc2(Organisation org, Region region, String regionCode) {
        List<CloudAsset> assets = new ArrayList<>();
        try (Ec2Client ec2 = ec2Client(region)) {
            String nextToken = null;
            do {
                var resp = ec2.describeInstances(DescribeInstancesRequest.builder()
                        .nextToken(nextToken).maxResults(200).build());
                for (var reservation : resp.reservations()) {
                    for (Instance i : reservation.instances()) {
                        if (i.instanceId() == null) continue;
                        String name = tagValue(i.tags(), "Name", i.instanceId());
                        CloudAssetStatus status = switch (i.state().nameAsString().toLowerCase()) {
                            case "running"                     -> CloudAssetStatus.RUNNING;
                            case "stopped", "stopping"         -> CloudAssetStatus.STOPPED;
                            case "terminated", "shutting-down" -> CloudAssetStatus.TERMINATED;
                            case "pending"                     -> CloudAssetStatus.PENDING;
                            default                            -> CloudAssetStatus.UNKNOWN;
                        };
                        assets.add(build(org, i.instanceId(), CloudProvider.AWS, regionCode,
                                CloudResourceType.VIRTUAL_MACHINE, name, status,
                                tagValue(i.tags(), "Environment", tagValue(i.tags(), "Env", null)),
                                tagsJson(i.tags()),
                                "EC2 " + i.instanceType().toString()));
                    }
                }
                nextToken = resp.nextToken();
            } while (nextToken != null);
            log.info("[AWS] EC2: {} instance(s) in {}", assets.size(), regionCode);
        } catch (SdkException ex) {
            log.warn("[AWS] EC2 scan failed in {}: {}", regionCode, ex.getMessage());
        }
        return assets;
    }

    // ── RDS ───────────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverRds(Organisation org, Region region, String regionCode) {
        List<CloudAsset> assets = new ArrayList<>();
        try (RdsClient rds = RdsClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create()).build()) {
            String marker = null;
            do {
                final String currentMarker = marker;
                var resp = rds.describeDBInstances(r -> r.marker(currentMarker).maxRecords(100));
                for (DBInstance db : resp.dbInstances()) {
                    CloudAssetStatus status = switch (db.dbInstanceStatus().toLowerCase()) {
                        case "available"             -> CloudAssetStatus.RUNNING;
                        case "stopped"               -> CloudAssetStatus.STOPPED;
                        case "deleting", "deleted"   -> CloudAssetStatus.TERMINATED;
                        case "starting", "creating",
                             "rebooting", "modifying" -> CloudAssetStatus.PENDING;
                        default                       -> CloudAssetStatus.UNKNOWN;
                    };
                    String desc = db.engine() + " " + db.engineVersion()
                            + " (" + db.dbInstanceClass() + ")";
                    assets.add(build(org, db.dbInstanceArn(), CloudProvider.AWS, regionCode,
                            CloudResourceType.DATABASE, db.dbInstanceIdentifier(),
                            status, null, null, desc));
                }
                marker = resp.marker();
            } while (marker != null && !marker.isBlank());
            log.info("[AWS] RDS: {} instance(s) in {}", assets.size(), regionCode);
        } catch (SdkException ex) {
            log.warn("[AWS] RDS scan failed in {}: {}", regionCode, ex.getMessage());
        }
        return assets;
    }

    // ── ElastiCache ──────────────────────────────────────────────────────────

    private List<CloudAsset> discoverElastiCache(Organisation org, Region region, String regionCode) {
        List<CloudAsset> assets = new ArrayList<>();
        try (ElastiCacheClient ec = ElastiCacheClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create()).build()) {
            String marker = null;
            do {
                final String currentMarker = marker;
                var resp = ec.describeCacheClusters(r -> r.marker(currentMarker).maxRecords(100)
                        .showCacheNodeInfo(false));
                for (CacheCluster c : resp.cacheClusters()) {
                    CloudAssetStatus status = switch (c.cacheClusterStatus().toLowerCase()) {
                        case "available"  -> CloudAssetStatus.RUNNING;
                        case "stopped"    -> CloudAssetStatus.STOPPED;
                        case "deleting"   -> CloudAssetStatus.TERMINATED;
                        case "creating", "modifying", "rebooting cluster nodes" -> CloudAssetStatus.PENDING;
                        default           -> CloudAssetStatus.UNKNOWN;
                    };
                    String desc = c.engine() + " " + c.engineVersion()
                            + " (" + c.cacheNodeType() + ", " + c.numCacheNodes() + " node(s))";
                    assets.add(build(org, c.cacheClusterId(), CloudProvider.AWS, regionCode,
                            CloudResourceType.CACHE, c.cacheClusterId(), status, null, null, desc));
                }
                marker = resp.marker();
            } while (marker != null && !marker.isBlank());
            log.info("[AWS] ElastiCache: {} cluster(s) in {}", assets.size(), regionCode);
        } catch (SdkException ex) {
            log.warn("[AWS] ElastiCache scan failed in {}: {}", regionCode, ex.getMessage());
        }
        return assets;
    }

    // ── Lambda ────────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverLambda(Organisation org, Region region, String regionCode) {
        List<CloudAsset> assets = new ArrayList<>();
        try (LambdaClient lambda = LambdaClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create()).build()) {
            String marker = null;
            do {
                final String currentMarker = marker;
                var resp = lambda.listFunctions(r -> r.marker(currentMarker).maxItems(100));
                for (FunctionConfiguration f : resp.functions()) {
                    String desc = f.runtime().toString() + " · " + f.memorySize() + "MB";
                    assets.add(build(org, f.functionArn(), CloudProvider.AWS, regionCode,
                            CloudResourceType.SERVERLESS_FUNCTION, f.functionName(),
                            CloudAssetStatus.RUNNING, null, null, desc));
                }
                marker = resp.nextMarker();
            } while (marker != null && !marker.isBlank());
            log.info("[AWS] Lambda: {} function(s) in {}", assets.size(), regionCode);
        } catch (SdkException ex) {
            log.warn("[AWS] Lambda scan failed in {}: {}", regionCode, ex.getMessage());
        }
        return assets;
    }

    // ── ELB (ALB + NLB) ──────────────────────────────────────────────────────

    private List<CloudAsset> discoverElb(Organisation org, Region region, String regionCode) {
        List<CloudAsset> assets = new ArrayList<>();
        try (ElasticLoadBalancingV2Client elb = ElasticLoadBalancingV2Client.builder()
                .region(region).credentialsProvider(DefaultCredentialsProvider.create()).build()) {
            String marker = null;
            do {
                final String currentMarker = marker;
                var resp = elb.describeLoadBalancers(r -> r.marker(currentMarker).pageSize(100));
                for (LoadBalancer lb : resp.loadBalancers()) {
                    CloudAssetStatus status = switch (lb.state().codeAsString().toLowerCase()) {
                        case "active"       -> CloudAssetStatus.RUNNING;
                        case "provisioning" -> CloudAssetStatus.PENDING;
                        case "failed"       -> CloudAssetStatus.STOPPED;
                        default             -> CloudAssetStatus.UNKNOWN;
                    };
                    String desc = lb.type().toString() + " · " + lb.scheme().toString();
                    assets.add(build(org, lb.loadBalancerArn(), CloudProvider.AWS, regionCode,
                            CloudResourceType.LOAD_BALANCER, lb.loadBalancerName(),
                            status, null, null, desc));
                }
                marker = resp.nextMarker();
            } while (marker != null && !marker.isBlank());
            log.info("[AWS] ELB: {} load balancer(s) in {}", assets.size(), regionCode);
        } catch (SdkException ex) {
            log.warn("[AWS] ELB scan failed in {}: {}", regionCode, ex.getMessage());
        }
        return assets;
    }

    // ── ECS ───────────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverEcs(Organisation org, Region region, String regionCode) {
        List<CloudAsset> assets = new ArrayList<>();
        try (EcsClient ecs = EcsClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create()).build()) {
            String nextToken = null;
            do {
                final String currentNextToken = nextToken;
                var listResp = ecs.listClusters(r -> r.nextToken(currentNextToken).maxResults(100));
                if (!listResp.clusterArns().isEmpty()) {
                    var descResp = ecs.describeClusters(r -> r.clusters(listResp.clusterArns()));
                    for (Cluster c : descResp.clusters()) {
                        CloudAssetStatus status = "ACTIVE".equalsIgnoreCase(c.status())
                                ? CloudAssetStatus.RUNNING : CloudAssetStatus.STOPPED;
                        String desc = c.registeredContainerInstancesCount()
                                + " container instance(s), "
                                + c.runningTasksCount() + " running task(s)";
                        assets.add(build(org, c.clusterArn(), CloudProvider.AWS, regionCode,
                                CloudResourceType.CONTAINER, c.clusterName(), status,
                                null, null, desc));
                    }
                }
                nextToken = listResp.nextToken();
            } while (nextToken != null && !nextToken.isBlank());
            log.info("[AWS] ECS: {} cluster(s) in {}", assets.size(), regionCode);
        } catch (SdkException ex) {
            log.warn("[AWS] ECS scan failed in {}: {}", regionCode, ex.getMessage());
        }
        return assets;
    }

    // ── EKS ───────────────────────────────────────────────────────────────────

    private List<CloudAsset> discoverEks(Organisation org, Region region, String regionCode) {
        List<CloudAsset> assets = new ArrayList<>();
        try (EksClient eks = EksClient.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create()).build()) {
            String nextToken = null;
            do {
                final String currentNextToken = nextToken;
                var listResp = eks.listClusters(r -> r.nextToken(currentNextToken).maxResults(100));
                for (String clusterName : listResp.clusters()) {
                    var desc = eks.describeCluster(r -> r.name(clusterName));
                    var c = desc.cluster();
                    CloudAssetStatus status = switch (c.status().toString().toUpperCase()) {
                        case "ACTIVE"   -> CloudAssetStatus.RUNNING;
                        case "CREATING",
                             "UPDATING" -> CloudAssetStatus.PENDING;
                        case "DELETING",
                             "FAILED"   -> CloudAssetStatus.TERMINATED;
                        default         -> CloudAssetStatus.UNKNOWN;
                    };
                    String arn = c.arn() != null ? c.arn() : "eks:" + regionCode + ":" + clusterName;
                    String description = "k8s " + c.version()
                            + (c.endpoint() != null ? " · " + c.endpoint() : "");
                    assets.add(build(org, arn, CloudProvider.AWS, regionCode,
                            CloudResourceType.KUBERNETES_CLUSTER, clusterName, status,
                            null, null, description));
                }
                nextToken = listResp.nextToken();
            } while (nextToken != null && !nextToken.isBlank());
            log.info("[AWS] EKS: {} cluster(s) in {}", assets.size(), regionCode);
        } catch (SdkException ex) {
            log.warn("[AWS] EKS scan failed in {}: {}", regionCode, ex.getMessage());
        }
        return assets;
    }

    // ── S3 (global) ───────────────────────────────────────────────────────────

    private List<CloudAsset> discoverS3(Organisation org, String seedRegion) {
        List<CloudAsset> assets = new ArrayList<>();
        try (S3Client s3 = S3Client.builder()
                .region(Region.of(seedRegion))
                .credentialsProvider(DefaultCredentialsProvider.create()).build()) {
            for (Bucket bucket : s3.listBuckets().buckets()) {
                String bucketRegion = "global";
                try {
                    var loc = s3.getBucketLocation(r -> r.bucket(bucket.name()));
                    String constraint = loc.locationConstraintAsString();
                    if (constraint != null && !constraint.isBlank()) bucketRegion = constraint;
                } catch (Exception ignored) { /* cross-account or permission denied */ }

                assets.add(build(org, "arn:aws:s3:::" + bucket.name(),
                        CloudProvider.AWS, bucketRegion,
                        CloudResourceType.STORAGE_BUCKET, bucket.name(),
                        CloudAssetStatus.RUNNING, null, null, "S3 bucket"));
            }
            log.info("[AWS] S3: {} bucket(s)", assets.size());
        } catch (SdkException ex) {
            log.warn("[AWS] S3 scan failed: {}", ex.getMessage());
        }
        return assets;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ec2Client ec2Client(Region region) {
        return Ec2Client.builder().region(region)
                .credentialsProvider(DefaultCredentialsProvider.create()).build();
    }

    private CloudAsset build(Organisation org, String resourceId, CloudProvider provider,
                             String region, CloudResourceType type, String name,
                             CloudAssetStatus status, String environment,
                             String tags, String description) {
        CloudAsset a = new CloudAsset();
        a.setOrganisation(org);
        a.setResourceId(resourceId);
        a.setProvider(provider);
        a.setRegion(region);
        a.setResourceType(type);
        a.setName(name != null ? name : resourceId);
        a.setStatus(status);
        a.setEnvironment(environment);
        if (tags != null) a.setTags(tags);
        if (description != null) a.setDescription(description);
        a.setLastSyncAt(Instant.now());
        return a;
    }

    private String tagValue(List<Tag> tags, String key, String fallback) {
        if (tags == null) return fallback;
        return tags.stream().filter(t -> key.equalsIgnoreCase(t.key()))
                .map(Tag::value).findFirst().orElse(fallback);
    }

    private String tagsJson(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) return null;
        return tags.stream()
                .map(t -> "\"" + esc(t.key()) + "\":\"" + esc(t.value()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
