package com.hypex.electriplan.projects.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.FloorPlanState;

import org.jspecify.annotations.Nullable;

/**
 * A row of a house's floor-plan history.
 *
 * @param current the version designs are made from (the newest committed one)
 * @param openChecks things the analyser was not sure of, plus a doubtful scale
 */
public record FloorPlanVersion(
        int versionNo,
        FloorPlanState state,
        boolean current,
        @Nullable String note,
        int rooms,
        int walls,
        int openings,
        @Nullable BigDecimal floorAreaM2,
        int openChecks,
        Instant savedAt,
        @Nullable UUID savedBy,
        @Nullable Instant committedAt) {
}
