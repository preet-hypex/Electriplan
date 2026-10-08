package com.hypex.electriplan.model.plan;

import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A room: its name as written on the plan (empty when none could be read), its
 * outline, where its name is drawn, and an optional fill colour for the editor.
 * The name is not yet a room type; enrichment (E1) maps it to one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Room(
        String id,
        String name,
        List<Point> polygon,
        Point labelPosition,
        @Nullable String colour,
        @Nullable Double confidence,
        @Nullable PlanItemSource source) {

    /** A colour as #rrggbb. */
    public static final Pattern COLOUR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public Room {
        Checks.id(id, "id");
        Checks.required(name, "name");
        polygon = Checks.sized(polygon, 3, Integer.MAX_VALUE, "polygon");
        Checks.required(labelPosition, "labelPosition");
        Checks.optionalMatches(colour, COLOUR, "colour");
        PlanChecks.confidence(confidence);
    }
}
