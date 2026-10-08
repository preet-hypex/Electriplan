package com.hypex.electriplan.model.plan;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A floor plan, in millimetres: what the engine designs from. Produced by the
 * analyser from an image and edited in the editor; the engine only reads it.
 * Follows {@code contracts/floor-plan.schema.json}. Read one with
 * {@link FloorPlanReader}, which also refuses versions this build does not know.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record FloorPlan(
        Integer version,
        String units,
        @Singular List<Wall> walls,
        @Singular List<Room> rooms,
        @Singular List<Door> doors,
        @Singular List<Window> windows,
        @Singular List<Opening> openings,
        @Singular List<Label> labels,
        @Singular List<Dimension> dimensions,
        @Nullable PlanSource source,
        @Nullable AnalysisReport analysis) {

    public static final int VERSION = 1;
    /** The versions this build reads. A plan of any other is refused. */
    public static final Set<Integer> SUPPORTED_VERSIONS = Set.of(VERSION);
    public static final String MILLIMETRES = "mm";

    public FloorPlan {
        Checks.oneOf(version, "version", VERSION);
        if (!MILLIMETRES.equals(Checks.required(units, "units"))) {
            throw new IllegalArgumentException("units must be \"mm\", was \"" + units + "\"");
        }
        walls = Checks.list(walls, "walls");
        rooms = Checks.list(rooms, "rooms");
        doors = Checks.list(doors, "doors");
        windows = Checks.list(windows, "windows");
        openings = Checks.list(openings, "openings");
        labels = Checks.list(labels, "labels");
        dimensions = Checks.list(dimensions, "dimensions");
    }

    /** A builder with the current version and millimetres already set. */
    public static FloorPlanBuilder builder() {
        return new FloorPlanBuilder().version(VERSION).units(MILLIMETRES);
    }
}
