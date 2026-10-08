package com.hypex.electriplan.files.dto;

/** A stored file's bytes, with what they are. Files are small (images up to 25 MB), so they are read whole. */
public record FileContent(byte[] bytes, String contentType, String sha256Hex) {
}
