package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A power in watts, never negative (luminaires, fans). Written to JSON as a plain number. */
public record Watts(@JsonValue double value) implements Comparable<Watts> {

    public Watts {
        Checks.nonNegative(value, "watts");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Watts of(double value) {
        return new Watts(value);
    }

    @Override
    public int compareTo(Watts other) {
        return Double.compare(value, other.value);
    }
}
