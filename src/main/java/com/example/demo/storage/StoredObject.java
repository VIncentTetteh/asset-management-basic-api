package com.example.demo.storage;

public record StoredObject(String contentType, String filename, byte[] bytes) {}
