package com.hypex.electriplan.files.domain;

/** The file store (S3) refused or did not answer. */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
