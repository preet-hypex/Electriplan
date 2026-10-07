package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Amperes;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** A device on the board: RCBO, RCD or MCB, its rating, curve, residual-current rating, and the circuits it protects. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record ProtectiveDevice(
        String id,
        DeviceKind kind,
        Amperes ratingA,
        @Nullable TripCurve curve,
        @Nullable Double rcdMa,
        @Singular List<String> circuits) {

    public ProtectiveDevice {
        Checks.id(id, "id");
        Checks.required(kind, "kind");
        Checks.positive(Checks.required(ratingA, "ratingA").value(), "ratingA");
        if (rcdMa != null) {
            Checks.positive(rcdMa, "rcdMa");
        }
        circuits = Checks.ids(circuits, "circuits");
    }
}
