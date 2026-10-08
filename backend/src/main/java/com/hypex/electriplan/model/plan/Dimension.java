package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A measurement written on the plan, drawn from start to end: its value in the
 * unit it was written in. Confirms the plan's scale.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Dimension(
        String id,
        Point start,
        Point end,
        Double value,
        DimensionUnit unit,
        @Nullable Double confidence,
        @Nullable PlanItemSource source) {

    public Dimension {
        Checks.id(id, "id");
        Checks.required(start, "start");
        Checks.required(end, "end");
        Checks.positive(Checks.required(value, "value"), "value");
        Checks.required(unit, "unit");
        PlanChecks.confidence(confidence);
    }
}
