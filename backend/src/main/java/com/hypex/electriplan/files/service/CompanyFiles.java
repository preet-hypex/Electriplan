package com.hypex.electriplan.files.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import com.hypex.electriplan.files.dao.StoredFileRepository;
import com.hypex.electriplan.files.domain.FilePurpose;
import com.hypex.electriplan.files.domain.UnsupportedFileException;
import com.hypex.electriplan.files.dto.FileContent;
import com.hypex.electriplan.files.dto.StoredFile;
import com.hypex.electriplan.files.entity.StoredFileEntity;
import com.hypex.electriplan.tenancy.domain.CompanyContext;
import com.hypex.electriplan.tenancy.service.CurrentCompany;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The request's company's files: storing an uploaded image (once, however
 * often it is uploaded) and reading one back. Bytes go to the {@link FileStore}
 * (S3); the record goes to electriplan.stored_file.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompanyFiles {

    /** The largest image people may upload. */
    public static final int MAX_IMAGE_BYTES = 25 * 1024 * 1024;

    private final FileStore store;
    private final StoredFileRepository files;
    private final CurrentCompany current;

    /**
     * Keeps an uploaded floor-plan image for the request's company: a JPG or
     * PNG up to 25 MB. The same bytes uploaded again give the same file.
     *
     * @throws UnsupportedFileException empty, too large, or not a JPG or PNG image
     */
    public StoredFile storeImage(byte[] bytes, @Nullable String originalName, FilePurpose purpose) {
        if (bytes.length == 0) {
            throw new UnsupportedFileException("The uploaded file was empty.");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new UnsupportedFileException("The image is %.1f MB; the limit is 25 MB.".formatted(bytes.length / 1e6));
        }
        ImageType type = ImageType.of(bytes);
        BufferedImage image = readImage(bytes);
        byte[] sha256 = sha256(bytes);

        Optional<StoredFileEntity> same = files.findFirstBySha256AndPurposeAndDeletedAtIsNull(sha256, purpose);
        if (same.isPresent()) {
            return view(same.get());
        }

        CompanyContext company = current.require();
        String key = "organisations/" + company.organisationId() + "/files/" + HexFormat.of().formatHex(sha256) + type.extension;
        store.put(key, bytes, type.contentType);
        StoredFileEntity file = StoredFileEntity.of(company.organisationId(), purpose, store.backend(), key,
                cleanName(originalName), type.contentType, bytes.length, sha256, image.getWidth(), image.getHeight(),
                company.userId());
        return view(files.saveAndFlush(file));
    }

    /** A file of the request's company, with its bytes; empty when there is none (or its bytes are gone). */
    @Transactional(readOnly = true)
    public Optional<FileContent> read(UUID id) {
        return files.findByIdAndDeletedAtIsNull(id)
                .flatMap(file -> store.get(file.getStorageKey())
                        .map(bytes -> new FileContent(bytes, file.getContentType(), HexFormat.of().formatHex(file.getSha256()))));
    }

    private static StoredFile view(StoredFileEntity f) {
        return new StoredFile(f.getId(), StoredFile.urlOf(f.getId()), f.getContentType(), f.getByteSize(), f.getOriginalName(),
                f.getImageWidth(), f.getImageHeight());
    }

    /** The image types the analyser reads, known by their first bytes (not by name or claimed type). */
    enum ImageType {
        PNG("image/png", ".png"),
        JPEG("image/jpeg", ".jpg");

        final String contentType;
        final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        static ImageType of(byte[] b) {
            if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
                return PNG;
            }
            if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
                return JPEG;
            }
            throw new UnsupportedFileException("That is not a JPG or PNG image. Upload a JPG or PNG of the floor plan.");
        }
    }

    private static BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new UnsupportedFileException("The image could not be read. Upload a JPG or PNG of the floor plan.");
            }
            return image;
        } catch (IOException e) {
            throw new UnsupportedFileException("The image could not be read: " + e.getMessage());
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** The name as uploaded, without any folders a browser may send. */
    private static @Nullable String cleanName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).strip();
        return base.isEmpty() ? null : base.length() > 200 ? base.substring(0, 200) : base;
    }

    /** The id as a UUID, or empty when it is not one. */
    public static Optional<UUID> idFrom(String text) {
        try {
            return Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
