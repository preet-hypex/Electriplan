package com.hypex.electriplan.files.domain;

/** A file the application does not take: the wrong type, empty, or too large. The message says what to do instead. */
public class UnsupportedFileException extends RuntimeException {

    public UnsupportedFileException(String message) {
        super(message);
    }
}
