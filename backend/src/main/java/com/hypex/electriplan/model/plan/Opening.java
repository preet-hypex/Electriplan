package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A gap in a wall that is neither clearly a door nor clearly a window: a cased
 * opening, or one whose symbol could not be read. The engine treats it as a
 * passage with no leaf.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Opening(
        String id,
        String wallId,
        Millimetres position,
        Millimetres width,
        @Nullable Double confidence,
        @Nullable PlanItemSource source) {

    public Opening {
        PlanChecks.inWall(id, wallId, position, width);
        PlanChecks.confidence(confidence);
    }
}
