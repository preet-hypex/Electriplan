package com.hypex.electriplan.projects.entity;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.HouseStage;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * One move of a house's stage (electriplan.plan_stage_event), written by the
 * database whenever the stage changes, with who made it. Read-only here.
 */
@Entity
@Immutable
@Table(schema = "electriplan", name = "plan_stage_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StageEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private long id;

    @Column(name = "plan_id", nullable = false)
    private UUID houseId;

    @Convert(converter = Codes.Stage.class)
    @Column(name = "from_stage")
    private @Nullable HouseStage from;

    @Convert(converter = Codes.Stage.class)
    @Column(name = "to_stage", nullable = false)
    private HouseStage to;

    @Column(name = "actor_id")
    private @Nullable UUID actorId;

    @Column(name = "note")
    private @Nullable String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
