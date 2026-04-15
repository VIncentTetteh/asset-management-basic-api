package com.assetiq.storage;

public record StoredObject(String contentType, String filename, byte[] bytes) {}
