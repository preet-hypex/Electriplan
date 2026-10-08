package com.hypex.electriplan.model.common;

import com.hypex.electriplan.model.Checks;

/**
 * A position in plan coordinates, in millimetres: origin at the top-left of the
 * drawing area, y downwards. Either coordinate may be negative.
 */
public record Point(double x, double y) {

    public Point {
        Checks.finite(x, "x");
        Checks.finite(y, "y");
    }
}
