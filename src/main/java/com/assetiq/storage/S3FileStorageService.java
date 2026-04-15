package com.assetiq.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Global S3 storage service backed by the application-level S3 credentials
 * configured via {@code app.storage.s3.*} properties.
 *
 * <p>This bean is only created when {@code app.storage.s3.enabled=true}.
 * It is NOT marked {@code @Primary} — {@link OrgAwareStorageService} is the
 * primary {@link FileStorageService} and delegates here when an organisation's
 * config has {@code useGlobalConfig=true}.
 */
@Service
@ConditionalOnProperty(name = "app.storage.s3.enabled", havingValue = "true")
public class S3FileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

    private final S3Client s3Client;
    private final S3Presigner presigner;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.kms-key-id:}")
    private String kmsKeyId;

    public S3FileStorageService(S3Client s3Client, S3Presigner presigner) {
        this.s3Client = s3Client;
        this.presigner = presigner;
    }

    @Override
    public StoredObject store(String key, byte[] bytes, String contentType, String filename, Map<String, String> metadata) {
        PutObjectRequest.Builder req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .acl(ObjectCannedACL.PRIVATE)
                .metadata(metadata == null ? Map.of() : metadata);

        if (filename != null && !filename.isBlank()) {
            req.contentDisposition("attachment; filename=\"" + filename + "\"");
        }

        if (kmsKeyId != null && !kmsKeyId.isBlank()) {
            req.serverSideEncryption(ServerSideEncryption.AWS_KMS)
               .ssekmsKeyId(kmsKeyId);
        } else {
            req.serverSideEncryption(ServerSideEncryption.AES256);
        }

        s3Client.putObject(req.build(), RequestBody.fromBytes(bytes));
        return new StoredObject(contentType, filename, bytes);
    }

    @Override
    public Optional<StoredObject> get(String key) {
        try {
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseInputStream<GetObjectResponse> resp = s3Client.getObject(req);
            byte[] bytes = resp.readAllBytes();
            String contentType = resp.response().contentType();
            String filename = null;
            return Optional.of(new StoredObject(contentType, filename, bytes));
        } catch (IOException e) {
            log.warn("[S3] Failed to read object {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> createPresignedGetUrl(String key, String filename, String contentType, Duration ttl) {
        try {
            GetObjectRequest.Builder getReq = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key);
            if (contentType != null && !contentType.isBlank()) {
                getReq.responseContentType(contentType);
            }
            if (filename != null && !filename.isBlank()) {
                getReq.responseContentDisposition("attachment; filename=\"" + filename + "\"");
            }

            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(getReq.build())
                    .build();
            return Optional.of(presigner.presignGetObject(presignReq).url().toString());
        } catch (Exception e) {
            log.warn("[S3] Failed to presign object {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
