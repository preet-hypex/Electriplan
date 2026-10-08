package com.hypex.electriplan.files.service;

import java.util.Optional;

/**
 * Where file bytes are kept, by key: S3's model (put and get an object in a
 * bucket). {@link S3FileStore} is the one the application uses; tests use
 * one in memory.
 */
public interface FileStore {

    /** What electriplan.stored_file.storage_backend records for files kept here. */
    String backend();

    void put(String key, byte[] content, String contentType);

    /** The object's bytes, if there is one under the key. */
    Optional<byte[]> get(String key);
}
