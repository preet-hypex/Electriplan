package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A door in a wall: its centre's distance from the wall's start, and its width.
 * What switch placement needs: how it opens ({@code style}, swinging when
 * absent), which jamb the hinge is on ({@code hingeAtStart}, in the wall's
 * start-to-end direction; the start when absent), and which side the leaf opens
 * to ({@code swing}: {@link #LEFT} for the wall's left-hand normal, {@link #RIGHT}
 * for its right). A sliding or garage door has no hinge or swing, so those two
 * are ignored for one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Door(
        String id,
        String wallId,
        Millimetres position,
        Millimetres width,
        @Nullable DoorStyle style,
        @Nullable Boolean hingeAtStart,
        @Nullable Double swing,
        @Nullable Double confidence,
        @Nullable PlanItemSource source) {

    /** The leaf opens to the wall's left-hand side. */
    public static final double LEFT = 90;
    /** The leaf opens to the wall's right-hand side. */
    public static final double RIGHT = -90;

    public Door {
        PlanChecks.inWall(id, wallId, position, width);
        if (swing != null && swing != LEFT && swing != RIGHT) {
            throw new IllegalArgumentException("swing must be 90 or -90, was " + swing);
        }
        PlanChecks.confidence(confidence);
    }

    /** How it opens: a door with no style swings. */
    public DoorStyle effectiveStyle() {
        return style == null ? DoorStyle.SWING : style;
    }
}
