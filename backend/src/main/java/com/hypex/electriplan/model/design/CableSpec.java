package com.hypex.electriplan.model.design;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Metres;
import com.hypex.electriplan.model.units.SquareMillimetres;

import lombok.Builder;
import lombok.With;

/** A circuit's cable: conductor size, type (e.g. TPS 2C+E) and total length. */
@Builder(toBuilder = true)
@With
public record CableSpec(SquareMillimetres csaMm2, String type, Metres lengthM) {

    public CableSpec {
        Checks.positive(Checks.required(csaMm2, "csaMm2").value(), "csaMm2");
        Checks.text(type, "cable.type");
        Checks.required(lengthM, "lengthM");
    }
}
