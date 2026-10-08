package com.hypex.electriplan.projects.entity;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.DwellingType;
import com.hypex.electriplan.projects.domain.HouseStage;

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
import org.hibernate.generator.EventType;
import org.jspecify.annotations.Nullable;

/** A house design in a project: electriplan.plan. */
@Entity
@Table(schema = "electriplan", name = "plan")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false)
    private String name;

    @Convert(converter = Codes.Dwelling.class)
    @Column(name = "dwelling_type", nullable = false)
    private DwellingType dwellingType;

    @Column(name = "storeys", nullable = false)
    private short storeys;

    /** Moved only along plan_stage_transition (database trigger); recorded in plan_stage_event. */
    @Convert(converter = Codes.Stage.class)
    @Column(name = "stage", nullable = false)
    private HouseStage stage;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "stage_changed_at", insertable = false, updatable = false)
    private Instant stageChangedAt;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private @Nullable Instant archivedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    public static HouseEntity create(UUID organisationId, UUID projectId, String name, DwellingType dwellingType,
                              @Nullable UUID createdBy) {
        HouseEntity house = new HouseEntity();
        house.name = name;
        house.dwellingType = dwellingType;
        house.id = UUID.randomUUID();
        house.organisationId = organisationId;
        house.projectId = projectId;
        house.createdBy = createdBy;
        house.stage = HouseStage.AWAITING_UPLOAD;
        house.storeys = 1;
        return house;
    }

    public boolean archived() {
        return archivedAt != null;
    }

    /**
     * A move to the next stage. Only along electriplan.plan_stage_transition:
     * the database refuses any other (see HouseStages).
     */
    public void moveTo(HouseStage next) {
        stage = next;
    }

    public void rename(String newName) {
        name = newName;
    }

    public void changeDwellingType(DwellingType type) {
        dwellingType = type;
    }

    /** Archiving twice changes nothing. */
    public void archive() {
        if (archivedAt == null) {
            archivedAt = Instant.now();
        }
    }

    public void restore() {
        archivedAt = null;
    }
}
