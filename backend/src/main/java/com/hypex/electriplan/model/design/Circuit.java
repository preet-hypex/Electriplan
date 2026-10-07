package com.hypex.electriplan.model.design;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Amperes;
import com.hypex.electriplan.model.units.Ohms;
import com.hypex.electriplan.model.units.Percent;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * A final subcircuit: what it supplies, its points, its protective device, and
 * as sizing fills them in, its demand, cable, voltage drop, fault-loop
 * impedance and route.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Circuit(
        String id,
        CircuitType type,
        String label,
        @Singular List<String> points,
        @Nullable String protectionId,
        @Nullable Amperes demandA,
        @Nullable CableSpec cable,
        @Nullable Percent voltageDropPct,
        @Nullable Ohms zsOhm,
        @Nullable Ohms zsMaxOhm,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @Singular("segment") List<RouteSegment> route) {

    public Circuit {
        Checks.id(id, "id");
        Checks.required(type, "type");
        Checks.text(label, "label");
        points = Checks.ids(points, "points");
        Checks.optionalId(protectionId, "protectionId");
        route = Checks.optionalList(route, "route");
    }
}
