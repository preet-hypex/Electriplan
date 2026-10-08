package com.hypex.electriplan.files.service;

import java.util.Optional;

import com.hypex.electriplan.files.domain.FileStorageException;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * Files in an S3 bucket: AWS in production, an S3-compatible store locally
 * (docker-compose's "s3" service). Keys are
 * {@code organisations/<company id>/files/<sha256>.<ext>}.
 */
public class S3FileStore implements FileStore {

    private final S3Client s3;
    private final String bucket;

    public S3FileStore(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public String backend() {
        return "s3";
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType).contentLength((long) content.length),
                    RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new FileStorageException("The file could not be stored: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(s3.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new FileStorageException("The file could not be read: " + e.getMessage(), e);
        }
    }
}
