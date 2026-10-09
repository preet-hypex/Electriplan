package com.hypex.electriplan.files.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** S3 in memory, for tests: objects by key. */
public class InMemoryFileStore implements FileStore {

    public final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    public final Map<String, String> contentTypes = new ConcurrentHashMap<>();

    @Override
    public String backend() {
        return "s3";
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, content.clone());
        contentTypes.put(key, contentType);
    }

    @Override
    public Optional<byte[]> get(String key) {
        return Optional.ofNullable(objects.get(key)).map(byte[]::clone);
    }
}
