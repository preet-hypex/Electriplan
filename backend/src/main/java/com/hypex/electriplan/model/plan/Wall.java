package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A wall's centre line from start to end, and its thickness measured
 * perpendicular to it. Switches and outlets mount on its faces; doors, windows
 * and openings sit in it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Wall(
        String id,
        Point start,
        Point end,
        Millimetres thickness,
        @Nullable Double confidence,
        @Nullable PlanItemSource source) {

    public Wall {
        Checks.id(id, "id");
        Checks.required(start, "start");
        Checks.required(end, "end");
        Checks.positive(Checks.required(thickness, "thickness").value(), "thickness");
        PlanChecks.confidence(confidence);
    }
}
