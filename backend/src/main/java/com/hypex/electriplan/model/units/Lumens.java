package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A light output in lumens, never negative. Written to JSON as a plain number. */
public record Lumens(@JsonValue double value) implements Comparable<Lumens> {

    public Lumens {
        Checks.nonNegative(value, "lumens");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Lumens of(double value) {
        return new Lumens(value);
    }

    @Override
    public int compareTo(Lumens other) {
        return Double.compare(value, other.value);
    }
}
