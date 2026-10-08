package com.hypex.electriplan.model.design;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Metres;

import lombok.Builder;
import lombok.With;

/** One run of a circuit's cable: from the board or a point, to a point. */
@Builder(toBuilder = true)
@With
public record RouteSegment(String from, String to, Metres lengthM) {

    public RouteSegment {
        Checks.text(from, "from");
        Checks.text(to, "to");
        Checks.required(lengthM, "lengthM");
    }
}
