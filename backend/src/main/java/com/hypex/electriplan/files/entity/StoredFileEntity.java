package com.hypex.electriplan.files.entity;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.files.domain.FilePurpose;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.jspecify.annotations.Nullable;

/** A file a company keeps (electriplan.stored_file): where its bytes are, and what they are. */
@Entity
@Table(schema = "electriplan", name = "stored_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredFileEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Convert(converter = PurposeCode.class)
    @Column(name = "purpose", nullable = false, updatable = false)
    private FilePurpose purpose;

    /** Which store holds the bytes ("s3"), and under which key. */
    @Column(name = "storage_backend", nullable = false, updatable = false)
    private String storageBackend;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "original_name", updatable = false)
    private @Nullable String originalName;

    @Column(name = "content_type", nullable = false, updatable = false)
    private String contentType;

    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;

    @Column(name = "sha256", nullable = false, updatable = false)
    private byte[] sha256;

    @Column(name = "image_width_px", updatable = false)
    private @Nullable Integer imageWidth;

    @Column(name = "image_height_px", updatable = false)
    private @Nullable Integer imageHeight;

    @Column(name = "uploaded_by", updatable = false)
    private @Nullable UUID uploadedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private @Nullable Instant deletedAt;

    @SuppressWarnings("java:S107") // one argument per column: a file is all of these at once
    public static StoredFileEntity of(UUID organisationId, FilePurpose purpose, String storageBackend, String storageKey,
                                      @Nullable String originalName, String contentType, long byteSize, byte[] sha256,
                                      @Nullable Integer imageWidth, @Nullable Integer imageHeight, @Nullable UUID uploadedBy) {
        StoredFileEntity file = new StoredFileEntity();
        file.id = UUID.randomUUID();
        file.organisationId = organisationId;
        file.purpose = purpose;
        file.storageBackend = storageBackend;
        file.storageKey = storageKey;
        file.originalName = originalName;
        file.contentType = contentType;
        file.byteSize = byteSize;
        file.sha256 = sha256.clone();
        file.imageWidth = imageWidth;
        file.imageHeight = imageHeight;
        file.uploadedBy = uploadedBy;
        return file;
    }

    @Converter
    static final class PurposeCode implements AttributeConverter<FilePurpose, String> {
        @Override public String convertToDatabaseColumn(FilePurpose value) { return value.code(); }
        @Override public FilePurpose convertToEntityAttribute(String code) { return FilePurpose.valueOf(code.toUpperCase(java.util.Locale.ROOT)); }
    }
}
