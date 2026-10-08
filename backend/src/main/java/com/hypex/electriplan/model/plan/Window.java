package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** A window in a wall: its centre's distance from the wall's start, and its width. Wall space no switch or outlet can use. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Window(
        String id,
        String wallId,
        Millimetres position,
        Millimetres width,
        @Nullable Double confidence,
        @Nullable PlanItemSource source) {

    public Window {
        PlanChecks.inWall(id, wallId, position, width);
        PlanChecks.confidence(confidence);
    }
}
