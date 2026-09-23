package com.assetiq.storage;

import com.assetiq.models.OrganisationStorageConfig;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.OrganisationStorageConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Primary {@link FileStorageService} that routes each storage operation to
 * the correct back-end for the current organisation.
 *
 * <p><b>Strategy — Option C (prefix-based isolation):</b>
 * All organisations share the application's IAM role (instance profile /
 * ECS task role).  No per-org credentials are stored.  Isolation is
 * enforced purely by scoping every S3 object key under the org's own prefix:
 *
 * <pre>
 *   {reportPrefix}/{orgId}/{objectId}/{filename}
 *   {importPrefix}/{orgId}/{objectId}/{filename}
 * </pre>
 *
 * <p><b>Resolution order:</b>
 * <ol>
 *   <li>The org has {@code s3Enabled=true} and the global S3 client exists → S3,
 *       routing to the org's own bucket override when it has one.
 *   <li>The global S3 client and platform bucket exist → S3 on the platform bucket.
 *       This is the hosted default: an organisation that has never been given a
 *       storage config still gets durable, prefix-isolated object storage.
 *   <li>Otherwise → the local durable backend
 *       ({@link FilesystemFileStorageService}, the self-hosted default).
 * </ol>
 *
 * <p>There is deliberately no in-memory step in that list. The heap map used to
 * be the silent last resort, which meant a misconfigured deployment lost every
 * uploaded file on restart and told nobody. {@link InMemoryFileStorageService}
 * now only exists when it is explicitly opted into, and
 * {@code StartupSecurityValidator} refuses to boot a deployment that would end
 * up on it outside a dev/test profile — so by the time a request reaches here,
 * a durable backend is guaranteed to be present.</p>
 */
@Service
@Primary
public class OrgAwareStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(OrgAwareStorageService.class);

    // ── Injected back-ends ────────────────────────────────────────────────────

    /** Present only when {@code app.storage.s3.enabled=true}. */
    @Autowired(required = false)
    private S3Client globalS3Client;

    @Autowired(required = false)
    private S3Presigner globalPresigner;

    /**
     * Durable local backend used when S3 is not in play. Filesystem when it is
     * enabled; the heap map only when that has been explicitly opted into.
     */
    private final FileStorageService localBackend;
    private final OrganisationStorageConfigRepository configRepository;

    @Value("${app.storage.s3.bucket:}")
    private String globalBucket;

    @Value("${app.storage.s3.kms-key-id:}")
    private String globalKmsKeyId;

    // ── Constructor ───────────────────────────────────────────────────────────

    public OrgAwareStorageService(Optional<FilesystemFileStorageService> filesystemBackend,
                                  Optional<InMemoryFileStorageService> inMemoryBackend,
                                  OrganisationStorageConfigRepository configRepository) {
        this.localBackend = filesystemBackend
                .map(FileStorageService.class::cast)
                .or(() -> inMemoryBackend.map(FileStorageService.class::cast))
                .orElse(null);
        this.configRepository = configRepository;
        if (localBackend == null) {
            log.info("[OrgAwareStorage] No local storage backend configured — every request "
                     + "must resolve to S3.");
        } else {
            log.info("[OrgAwareStorage] Local storage backend: {}",
                     localBackend.getClass().getSimpleName());
        }
    }

    /**
     * The durable non-S3 backend, or a hard failure.
     *
     * <p>Reaching this with nothing configured means S3 was expected and is not
     * usable for this request. Failing loudly beats the previous behaviour of
     * quietly writing the file into a heap map that the next restart discards.</p>
     */
    private FileStorageService local() {
        if (localBackend == null) {
            throw new IllegalStateException(
                    "No durable file storage is available for this request. "
                    + "Enable object storage (APP_STORAGE_S3_ENABLED=true with "
                    + "APP_STORAGE_S3_BUCKET) or filesystem storage "
                    + "(APP_STORAGE_FILESYSTEM_ENABLED=true with APP_STORAGE_FILESYSTEM_BASE_DIR).");
        }
        return localBackend;
    }

    // ── FileStorageService ────────────────────────────────────────────────────

    @Override
    public StoredObject store(String key, byte[] bytes, String contentType,
                              String filename, Map<String, String> metadata) {
        Resolution r = resolve();
        if (!r.useS3()) return local().store(key, bytes, contentType, filename, metadata);

        PutObjectRequest.Builder req = PutObjectRequest.builder()
                .bucket(r.bucket())
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PRIVATE)
                .metadata(metadata == null ? Map.of() : metadata);

        if (filename != null && !filename.isBlank()) {
            req.contentDisposition("attachment; filename=\"" + filename + "\"");
        }
        if (r.kmsKeyId() != null && !r.kmsKeyId().isBlank()) {
            req.serverSideEncryption(ServerSideEncryption.AWS_KMS).ssekmsKeyId(r.kmsKeyId());
        } else {
            req.serverSideEncryption(ServerSideEncryption.AES256);
        }

        globalS3Client.putObject(req.build(), RequestBody.fromBytes(bytes));
        return new StoredObject(contentType, filename, bytes);
    }

    @Override
    public Optional<StoredObject> get(String key) {
        Resolution r = resolve();
        if (!r.useS3()) return local().get(key);

        try {
            var resp = globalS3Client.getObject(b -> b.bucket(r.bucket()).key(key));
            byte[] bytes = resp.readAllBytes();
            return Optional.of(new StoredObject(resp.response().contentType(), null, bytes));
        } catch (Exception e) {
            log.warn("[OrgAwareStorage] Failed to read s3://{}/{}: {}", r.bucket(), key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> createPresignedGetUrl(String key, String filename,
                                                   String contentType, Duration ttl) {
        Resolution r = resolve();
        if (!r.useS3()) return local().createPresignedGetUrl(key, filename, contentType, ttl);

        try {
            var getReq = GetObjectRequest.builder().bucket(r.bucket()).key(key);
            if (contentType != null && !contentType.isBlank()) getReq.responseContentType(contentType);
            if (filename    != null && !filename.isBlank())
                getReq.responseContentDisposition("attachment; filename=\"" + filename + "\"");

            var presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getReq.build())
                    .build();
            return Optional.of(globalPresigner.presignGetObject(presignReq).url().toString());
        } catch (Exception e) {
            log.warn("[OrgAwareStorage] Failed to presign s3://{}/{}: {}", r.bucket(), key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        Resolution r = resolve();
        if (!r.useS3()) { local().delete(key); return; }
        globalS3Client.deleteObject(b -> b.bucket(r.bucket()).key(key));
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    private record Resolution(boolean useS3, String bucket, String kmsKeyId) {}

    private Resolution resolve() {
        UUID orgId = TenantContext.getOrganisationId();

        Optional<OrganisationStorageConfig> configOpt = orgId == null
                ? Optional.empty()
                : configRepository.findByOrganisationIdAndDeletedAtIsNull(orgId);

        boolean orgOptedIntoS3 = configOpt.isPresent() && configOpt.get().isS3Enabled();

        if (globalS3Client == null) {
            if (orgOptedIntoS3) {
                log.warn("[OrgAwareStorage] org={} has s3Enabled=true but app.storage.s3.enabled=false "
                         + "— using the local durable backend instead. "
                         + "Set APP_STORAGE_S3_ENABLED=true to enable S3.", orgId);
            }
            return noS3("Global S3 client not initialised");
        }

        // Org-specific bucket override, or the platform bucket.
        String bucket = orgOptedIntoS3
                        && configOpt.get().getBucketName() != null
                        && !configOpt.get().getBucketName().isBlank()
                ? configOpt.get().getBucketName()
                : globalBucket;

        if (bucket == null || bucket.isBlank()) {
            log.warn("[OrgAwareStorage] S3 is enabled but no bucket is configured "
                     + "(app.storage.s3.bucket is empty) — using the local durable backend.");
            return noS3("No S3 bucket configured");
        }

        log.debug("[OrgAwareStorage] org={} → s3://{}", orgId, bucket);
        return new Resolution(true, bucket, globalKmsKeyId);
    }

    private Resolution noS3(String reason) {
        log.debug("[OrgAwareStorage] Using the local durable backend — {}", reason);
        return new Resolution(false, null, null);
    }
}
