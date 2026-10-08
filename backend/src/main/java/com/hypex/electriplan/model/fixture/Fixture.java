package com.hypex.electriplan.model.fixture;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.common.WallAnchor;
import com.hypex.electriplan.model.units.Kilowatts;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A fixed item the electrical design depends on: a shower or basin (wet-area
 * zones are measured from it), a bench (kitchen outlets follow it), an appliance
 * (a dedicated circuit), the switchboard (where every cable starts).
 * Follows {@code contracts/fixture.schema.json}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Fixture(
        String id,
        FixtureKind kind,
        @Nullable String roomId,
        Footprint footprint,
        @Nullable WallAnchor wallAnchor,
        @Nullable Point waterOutlet,
        @Nullable Millimetres heightMm,
        @Nullable Kilowatts ratingKw,
        FixtureSource source,
        @Nullable Double confidence) {

    public Fixture {
        Checks.id(id, "id");
        Checks.required(kind, "kind");
        Checks.optionalId(roomId, "roomId");
        Checks.required(footprint, "footprint");
        if (ratingKw != null) {
            Checks.positive(ratingKw.value(), "ratingKw");
        }
        Checks.required(source, "source");
        if (confidence != null) {
            Checks.between(confidence, 0, 1, "confidence");
        }
    }
}
