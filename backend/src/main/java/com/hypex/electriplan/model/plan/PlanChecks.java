package com.hypex.electriplan.model.plan;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Millimetres;

import org.jspecify.annotations.Nullable;

/** The checks the plan's records share, beside the model-wide {@link Checks}. */
final class PlanChecks {

    private PlanChecks() {
    }

    /** A detected item's confidence: absent, or a fraction from 0 to 1. */
    static void confidence(@Nullable Double confidence) {
        if (confidence != null) {
            Checks.between(confidence, 0, 1, "confidence");
        }
    }

    /** Where an item sits in a wall, and how wide it is: doors, windows and openings. */
    static void inWall(String id, String wallId, Millimetres position, Millimetres width) {
        Checks.id(id, "id");
        Checks.id(wallId, "wallId");
        Checks.required(position, "position");
        Checks.positive(Checks.required(width, "width").value(), "width");
    }
}
