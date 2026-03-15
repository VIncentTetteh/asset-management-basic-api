package com.example.demo.storage;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryFileStorageService implements FileStorageService {

    private final ConcurrentHashMap<String, StoredObject> store = new ConcurrentHashMap<>();

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
