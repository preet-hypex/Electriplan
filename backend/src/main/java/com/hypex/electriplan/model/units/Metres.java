package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A length in metres, never negative (cable runs, consumer mains). Written to JSON as a plain number. */
public record Metres(@JsonValue double value) implements Comparable<Metres> {

    public Metres {
        Checks.nonNegative(value, "metres");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Metres of(double value) {
        return new Metres(value);
    }

    @Override
    public int compareTo(Metres other) {
        return Double.compare(value, other.value);
    }
}
