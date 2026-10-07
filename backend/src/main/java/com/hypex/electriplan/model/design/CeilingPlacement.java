package com.hypex.electriplan.model.design;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.Point;

/** On the ceiling, at a point on plan. */
public record CeilingPlacement(Point at) implements Placement {

    public CeilingPlacement {
        Checks.required(at, "at");
    }
}
