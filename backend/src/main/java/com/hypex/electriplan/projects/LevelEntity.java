package com.hypex.electriplan.projects;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** A storey of a house: electriplan.plan_level. v1 creates the ground floor with the house. */
@Entity
@Table(schema = "electriplan", name = "plan_level")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LevelEntity {

    static final String GROUND_FLOOR = "Ground floor";
    static final int DEFAULT_CEILING_MM = 2550;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false, updatable = false)
    private UUID organisationId;

    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID houseId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "ordinal", nullable = false)
    private short ordinal;

    @Column(name = "ceiling_height_mm", nullable = false)
    private int ceilingHeightMm;

    @Column(name = "current_floor_plan_version_id", insertable = false, updatable = false)
    private @Nullable UUID currentFloorPlanVersionId;

    static LevelEntity groundFloor(HouseEntity house) {
        LevelEntity level = new LevelEntity();
        level.id = UUID.randomUUID();
        level.organisationId = house.getOrganisationId();
        level.houseId = house.getId();
        level.name = GROUND_FLOOR;
        level.ordinal = 0;
        level.ceilingHeightMm = DEFAULT_CEILING_MM;
        return level;
    }
}
