package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** An impedance in ohms, never negative (earth fault loop impedance). Written to JSON as a plain number. */
public record Ohms(@JsonValue double value) implements Comparable<Ohms> {

    public Ohms {
        Checks.nonNegative(value, "ohms");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Ohms of(double value) {
        return new Ohms(value);
    }

    @Override
    public int compareTo(Ohms other) {
        return Double.compare(value, other.value);
    }
}
