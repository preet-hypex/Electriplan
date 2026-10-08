package com.hypex.electriplan.files.dto;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A file the company keeps.
 *
 * @param url where members of the company fetch it: {@code /api/files/{id}}
 */
public record StoredFile(
        UUID id,
        String url,
        String contentType,
        long byteSize,
        @Nullable String originalName,
        @Nullable Integer imageWidth,
        @Nullable Integer imageHeight) {

    public static String urlOf(UUID id) {
        return "/api/files/" + id;
    }
}
