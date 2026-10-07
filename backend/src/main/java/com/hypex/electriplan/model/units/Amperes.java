package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A current in amperes, never negative (demand, device ratings). Written to JSON as a plain number. */
public record Amperes(@JsonValue double value) implements Comparable<Amperes> {

    public Amperes {
        Checks.nonNegative(value, "amperes");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Amperes of(double value) {
        return new Amperes(value);
    }

    @Override
    public int compareTo(Amperes other) {
        return Double.compare(value, other.value);
    }
}
