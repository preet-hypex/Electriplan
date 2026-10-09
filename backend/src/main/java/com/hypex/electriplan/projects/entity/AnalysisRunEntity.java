package com.hypex.electriplan.projects.entity;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.AnalysisStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * One run of the floor-plan analyser on an uploaded image
 * (electriplan.analysis_run): what it was asked, how it went, how long it took.
 * Kept whether it succeeded or failed.
 */
@Entity
@Table(schema = "electriplan", name = "analysis_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisRunEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Column(name = "plan_level_id", nullable = false, updatable = false)
    private UUID levelId;

    @Column(name = "source_file_id", nullable = false, updatable = false)
    private UUID sourceFileId;

    @Convert(converter = Codes.RunStatus.class)
    @Column(name = "status", nullable = false)
    private AnalysisStatus status;

    /** What the analyser was asked, e.g. {"mmPerPx": 12.5}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false, updatable = false)
    private String parameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "steps", nullable = false)
    private String steps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false)
    private String warnings;

    @Column(name = "error_message")
    private @Nullable String errorMessage;

    @Column(name = "requested_by", updatable = false)
    private @Nullable UUID requestedBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "queued_at", insertable = false, updatable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private @Nullable Instant startedAt;

    @Column(name = "finished_at")
    private @Nullable Instant finishedAt;

    /** A run starting now. */
    public static AnalysisRunEntity starting(UUID organisationId, UUID levelId, UUID sourceFileId, String parametersJson,
                                             @Nullable UUID requestedBy) {
        AnalysisRunEntity run = new AnalysisRunEntity();
        run.id = UUID.randomUUID();
        run.organisationId = organisationId;
        run.levelId = levelId;
        run.sourceFileId = sourceFileId;
        run.status = AnalysisStatus.RUNNING;
        run.parameters = parametersJson;
        run.steps = "[]";
        run.warnings = "[]";
        run.requestedBy = requestedBy;
        run.startedAt = Instant.now();
        return run;
    }

    public void succeeded(String stepsJson, String warningsJson) {
        status = AnalysisStatus.SUCCEEDED;
        steps = stepsJson;
        warnings = warningsJson;
        finishedAt = Instant.now();
    }

    public void failed(String message) {
        status = AnalysisStatus.FAILED;
        errorMessage = message;
        finishedAt = Instant.now();
    }
}
