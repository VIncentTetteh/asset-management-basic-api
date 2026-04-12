package com.example.demo.storage;

import com.example.demo.models.OrganisationStorageConfig;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.OrganisationStorageConfigRepository;
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
 *   <li>If no tenant context is present → in-memory fallback.
 *   <li>If the org has no storage config, or {@code s3Enabled=false} → in-memory fallback.
 *   <li>If {@code s3Enabled=true} and the global S3 client is available → use S3
 *       (optionally routing to the org's own bucket override).
 *   <li>If {@code s3Enabled=true} but the global S3 client is absent
 *       ({@code app.storage.s3.enabled=false}) → log a warning and fall back to in-memory.
 * </ol>
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

    private final InMemoryFileStorageService inMemoryFallback;
    private final OrganisationStorageConfigRepository configRepository;

    @Value("${app.storage.s3.bucket:}")
    private String globalBucket;

    @Value("${app.storage.s3.kms-key-id:}")
    private String globalKmsKeyId;

    // ── Constructor ───────────────────────────────────────────────────────────

    public OrgAwareStorageService(InMemoryFileStorageService inMemoryFallback,
                                  OrganisationStorageConfigRepository configRepository) {
        this.inMemoryFallback  = inMemoryFallback;
        this.configRepository  = configRepository;
    }

    // ── FileStorageService ────────────────────────────────────────────────────

    @Override
    public StoredObject store(String key, byte[] bytes, String contentType,
                              String filename, Map<String, String> metadata) {
        Resolution r = resolve();
        if (!r.useS3()) return inMemoryFallback.store(key, bytes, contentType, filename, metadata);

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
        if (!r.useS3()) return inMemoryFallback.get(key);

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
        if (!r.useS3()) return inMemoryFallback.createPresignedGetUrl(key, filename, contentType, ttl);

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
        if (!r.useS3()) { inMemoryFallback.delete(key); return; }
        globalS3Client.deleteObject(b -> b.bucket(r.bucket()).key(key));
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    private record Resolution(boolean useS3, String bucket, String kmsKeyId) {}

    private Resolution resolve() {
        UUID orgId = TenantContext.getOrganisationId();
        if (orgId == null) {
            return noS3("No tenant context");
        }

        Optional<OrganisationStorageConfig> configOpt =
                configRepository.findByOrganisationIdAndDeletedAtIsNull(orgId);

        if (configOpt.isEmpty() || !configOpt.get().isS3Enabled()) {
            return noS3("S3 disabled or no config for org=" + orgId);
        }

        if (globalS3Client == null) {
            log.warn("[OrgAwareStorage] org={} has s3Enabled=true but app.storage.s3.enabled=false " +
                     "— falling back to in-memory. Set APP_STORAGE_S3_ENABLED=true to enable S3.", orgId);
            return noS3("Global S3 client not initialised");
        }

        OrganisationStorageConfig cfg = configOpt.get();
        // Org-specific bucket override, or fall back to the global bucket
        String bucket = (cfg.getBucketName() != null && !cfg.getBucketName().isBlank())
                ? cfg.getBucketName()
                : globalBucket;

        log.debug("[OrgAwareStorage] org={} → s3://{}", orgId, bucket);
        return new Resolution(true, bucket, globalKmsKeyId);
    }

    private Resolution noS3(String reason) {
        log.debug("[OrgAwareStorage] Using in-memory storage — {}", reason);
        return new Resolution(false, null, null);
    }
}
