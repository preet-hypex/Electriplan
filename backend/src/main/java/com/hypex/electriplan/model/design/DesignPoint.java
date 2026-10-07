package com.hypex.electriplan.model.design;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Source;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * One electrical item: a light, switch, outlet, fan, smoke alarm... on a wall
 * or the ceiling, with the circuit it is on, what controls it (or what it
 * controls), and why the engine put it there.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record DesignPoint(
        String id,
        PointKind kind,
        @Nullable String roomId,
        Placement placement,
        PointSpec spec,
        @Nullable String circuitId,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @Singular("controlledBy") List<String> controlledBy,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @Singular("gang") List<List<String>> controls,
        @Singular("rationale") List<String> rationale,
        Source source) {

    public DesignPoint {
        Checks.id(id, "id");
        Checks.required(kind, "kind");
        Checks.optionalId(roomId, "roomId");
        Checks.required(placement, "placement");
        Checks.required(spec, "spec");
        Checks.optionalId(circuitId, "circuitId");
        controlledBy = Checks.optionalIds(controlledBy, "controlledBy");
        controls = Checks.optionalList(controls, "controls").stream()
                .map(gang -> Checks.ids(gang, "controls[]"))
                .toList();
        rationale = Checks.list(rationale, "rationale");
        Checks.required(source, "source");
    }
}
