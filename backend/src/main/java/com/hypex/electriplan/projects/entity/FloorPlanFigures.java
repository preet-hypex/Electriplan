package com.hypex.electriplan.projects.entity;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * What lists show about a floor plan without reading it: counts, area, how
 * many things still need checking, the scale, and a hash of its contents.
 */
public record FloorPlanFigures(
        int rooms,
        int walls,
        int openings,
        BigDecimal floorAreaM2,
        int openChecks,
        @Nullable BigDecimal mmPerPx,
        @Nullable BigDecimal scaleConfidence,
        @Nullable String scaleMethod,
        byte[] sha256) {
}
