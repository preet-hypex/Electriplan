package com.hypex.electriplan.projects.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.FloorPlanOrigin;
import com.hypex.electriplan.projects.domain.FloorPlanState;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A version of a storey's floor plan (electriplan.floor_plan_version): the
 * FloorPlan JSON and figures read from it. Each storey has at most one draft
 * (the one being edited); committed versions are read-only, which the
 * database enforces.
 */
@Entity
@Table(schema = "electriplan", name = "floor_plan_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FloorPlanVersionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Column(name = "plan_level_id", nullable = false, updatable = false)
    private UUID levelId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Convert(converter = Codes.PlanState.class)
    @Column(name = "state", nullable = false)
    private FloorPlanState state;

    @Convert(converter = Codes.Origin.class)
    @Column(name = "origin", nullable = false)
    private FloorPlanOrigin origin;

    @Column(name = "based_on_version_id")
    private @Nullable UUID basedOnVersionId;

    /** The FloorPlan, as JSON (contracts/floor-plan.schema.json). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document", nullable = false)
    private String document;

    @Column(name = "scale_mm_per_px")
    private @Nullable BigDecimal scaleMmPerPx;

    @Column(name = "scale_confidence")
    private @Nullable BigDecimal scaleConfidence;

    @Column(name = "scale_method")
    private @Nullable String scaleMethod;

    @Column(name = "room_count", nullable = false)
    private int roomCount;

    @Column(name = "wall_count", nullable = false)
    private int wallCount;

    @Column(name = "opening_count", nullable = false)
    private int openingCount;

    @Column(name = "floor_area_m2")
    private @Nullable BigDecimal floorAreaM2;

    @Column(name = "open_check_count", nullable = false)
    private int openCheckCount;

    @Column(name = "content_sha256")
    private byte @Nullable [] contentSha256;

    @Column(name = "note")
    private @Nullable String note;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "committed_by")
    private @Nullable UUID committedBy;

    @Column(name = "committed_at")
    private @Nullable Instant committedAt;

    /** A new draft for a storey, numbered after its last version. */
    public static FloorPlanVersionEntity draft(UUID organisationId, UUID levelId, int versionNo, FloorPlanOrigin origin,
                                               @Nullable UUID basedOnVersionId, @Nullable UUID createdBy) {
        FloorPlanVersionEntity draft = new FloorPlanVersionEntity();
        draft.id = UUID.randomUUID();
        draft.organisationId = organisationId;
        draft.levelId = levelId;
        draft.versionNo = versionNo;
        draft.state = FloorPlanState.DRAFT;
        draft.origin = origin;
        draft.basedOnVersionId = basedOnVersionId;
        draft.createdBy = createdBy;
        return draft;
    }

    /** New contents for the draft, with the figures read from them. */
    public void replaceDocument(String json, FloorPlanFigures figures) {
        document = json;
        roomCount = figures.rooms();
        wallCount = figures.walls();
        openingCount = figures.openings();
        floorAreaM2 = figures.floorAreaM2();
        openCheckCount = figures.openChecks();
        scaleMmPerPx = figures.mmPerPx();
        scaleConfidence = figures.scaleConfidence();
        scaleMethod = figures.scaleMethod();
        contentSha256 = figures.sha256();
    }

    /** Based on another version now (a restore). */
    public void basedOn(UUID versionId) {
        basedOnVersionId = versionId;
    }

    /** Frozen: read-only from now on. */
    public void commit(@Nullable String withNote, @Nullable UUID by) {
        state = FloorPlanState.COMMITTED;
        note = withNote;
        committedBy = by;
        committedAt = Instant.now();
    }

    public boolean isDraft() {
        return state == FloorPlanState.DRAFT;
    }
}
