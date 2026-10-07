package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A conductor cross-section in mm², never negative. Written to JSON as a plain number. */
public record SquareMillimetres(@JsonValue double value) implements Comparable<SquareMillimetres> {

    public SquareMillimetres {
        Checks.nonNegative(value, "square millimetres");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static SquareMillimetres of(double value) {
        return new SquareMillimetres(value);
    }

    @Override
    public int compareTo(SquareMillimetres other) {
        return Double.compare(value, other.value);
    }
}
