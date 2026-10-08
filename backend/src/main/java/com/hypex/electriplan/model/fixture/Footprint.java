package com.hypex.electriplan.model.fixture;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;
import com.hypex.electriplan.model.units.Millimetres;

import lombok.Builder;
import lombok.With;

/** A fixture's outline from above: centre, width along its own x axis, depth, rotation clockwise in degrees. */
@Builder(toBuilder = true)
@With
public record Footprint(Point centre, Millimetres width, Millimetres depth, Double rotationDeg) {

    public Footprint {
        Checks.required(centre, "centre");
        Checks.positive(Checks.required(width, "width").value(), "width");
        Checks.positive(Checks.required(depth, "depth").value(), "depth");
        Checks.between(Checks.required(rotationDeg, "rotationDeg"), -360, 360, "rotationDeg");
    }
}
