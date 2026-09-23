package com.assetiq.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heap-backed storage for tests and throwaway local runs. <strong>Never for a
 * deployment that holds real data.</strong>
 *
 * <p>The map is per-process, never evicted and holds whole file bodies, so every
 * uploaded document is lost on restart, invisible to a second replica, and an
 * unbounded path to OOM. That is data loss, not a degraded mode, which is why
 * this bean no longer exists unless {@code app.storage.in-memory.enabled=true}
 * is set explicitly. {@code StartupSecurityValidator} additionally refuses to
 * boot when the opt-in is present outside a dev/test/local profile.</p>
 *
 * <p>Anything that needs durable storage without S3 should use
 * {@link FilesystemFileStorageService}.</p>
 */
@Service
@ConditionalOnProperty(prefix = "app.storage.in-memory", name = "enabled", havingValue = "true")
public class InMemoryFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryFileStorageService.class);

    private final ConcurrentHashMap<String, StoredObject> store = new ConcurrentHashMap<>();

    @PostConstruct
    void warn() {
        log.warn("[STORAGE] In-memory file storage is ACTIVE (app.storage.in-memory.enabled=true). "
                 + "Uploaded files live in this JVM's heap and are lost on restart. "
                 + "This is for tests and local development only.");
    }

    @Override
    public StoredObject store(String key, byte[] bytes, String contentType, String filename, Map<String, String> metadata) {
        StoredObject obj = new StoredObject(contentType, filename, bytes);
        store.put(key, obj);
        return obj;
    }

    @Override
    public Optional<StoredObject> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public Optional<String> createPresignedGetUrl(String key, String filename, String contentType, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }
}
