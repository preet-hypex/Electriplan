package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** Text on the plan, at the centre of where it is written, and the room it names when it was matched to one. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Label(
        String id,
        String text,
        Point position,
        LabelKind type,
        @Nullable String roomId,
        @Nullable Double confidence,
        @Nullable PlanItemSource source) {

    public Label {
        Checks.id(id, "id");
        Checks.required(text, "text");
        Checks.required(position, "position");
        Checks.required(type, "type");
        Checks.optionalId(roomId, "roomId");
        PlanChecks.confidence(confidence);
    }
}
