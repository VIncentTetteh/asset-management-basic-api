package com.example.demo.storage;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public interface FileStorageService {
    StoredObject store(String key, byte[] bytes, String contentType, String filename, Map<String, String> metadata);

    Optional<StoredObject> get(String key);

    Optional<String> createPresignedGetUrl(String key, String filename, String contentType, Duration ttl);

    void delete(String key);
}
