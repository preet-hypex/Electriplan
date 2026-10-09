package com.hypex.electriplan.projects.entity;

import java.io.Serializable;

import com.hypex.electriplan.projects.domain.HouseStage;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** A move a house's stage may make (electriplan.plan_stage_transition): the workflow, as data. Read-only. */
@Entity
@Immutable
@Table(schema = "electriplan", name = "plan_stage_transition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StageTransitionEntity {

    @EmbeddedId
    private Move move;

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Move implements Serializable {

        @Convert(converter = Codes.Stage.class)
        @Column(name = "from_stage", nullable = false)
        private HouseStage from;

        @Convert(converter = Codes.Stage.class)
        @Column(name = "to_stage", nullable = false)
        private HouseStage to;
    }
}
