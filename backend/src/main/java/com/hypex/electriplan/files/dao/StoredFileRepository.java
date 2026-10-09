package com.hypex.electriplan.files.dao;

import java.util.Optional;
import java.util.UUID;

import com.hypex.electriplan.files.domain.FilePurpose;
import com.hypex.electriplan.files.entity.StoredFileEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/** The request's company's files (row-level security hides every other company's). */
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    /** The same bytes, already kept for the same purpose: stored once. */
    Optional<StoredFileEntity> findFirstBySha256AndPurposeAndDeletedAtIsNull(byte[] sha256, FilePurpose purpose);

    Optional<StoredFileEntity> findByIdAndDeletedAtIsNull(UUID id);
}
