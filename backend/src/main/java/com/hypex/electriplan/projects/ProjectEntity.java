package com.hypex.electriplan.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.hypex.electriplan.model.common.AustralianState;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.jspecify.annotations.Nullable;

/** A project (electriplan.project): a job at one site. */
@Entity
@Table(schema = "electriplan", name = "project")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ProjectEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private UUID organisationId;

    /** Assigned by the database on insert (PRJ-000001...). */
    @Generated(event = EventType.INSERT)
    @Column(name = "reference", insertable = false, updatable = false)
    private String reference;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private @Nullable String description;

    @Convert(converter = Codes.Status.class)
    @Column(name = "status", nullable = false)
    private ProjectStatus status;

    @Column(name = "lot_number")
    private @Nullable String lotNumber;

    @Column(name = "site_street")
    private @Nullable String siteStreet;

    @Column(name = "site_suburb")
    private @Nullable String siteSuburb;

    @Enumerated(EnumType.STRING)
    @Column(name = "site_state", nullable = false)
    private AustralianState siteState;

    @Column(name = "site_postcode")
    private @Nullable String sitePostcode;

    @Column(name = "distributor_code")
    private @Nullable String distributorCode;

    @Column(name = "supply_phases", nullable = false)
    private short supplyPhases;

    @Column(name = "due_on")
    private @Nullable LocalDate dueOn;

    @Column(name = "created_by", updatable = false)
    private @Nullable UUID createdBy;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** When the project or one of its houses last changed (database triggers). */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "last_activity_at", insertable = false, updatable = false)
    private Instant lastActivityAt;

    @Column(name = "archived_at")
    private @Nullable Instant archivedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    static ProjectEntity create(UUID organisationId, @Nullable UUID createdBy) {
        ProjectEntity project = new ProjectEntity();
        project.id = UUID.randomUUID();
        project.organisationId = organisationId;
        project.createdBy = createdBy;
        project.status = ProjectStatus.ACTIVE;
        project.supplyPhases = 1;
        return project;
    }

    boolean archived() {
        return archivedAt != null;
    }
}
