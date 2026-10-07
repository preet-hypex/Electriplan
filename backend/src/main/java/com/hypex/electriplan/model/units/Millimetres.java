package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A length or distance in millimetres, never negative. Written to JSON as a plain number. */
public record Millimetres(@JsonValue double value) implements Comparable<Millimetres> {

    public Millimetres {
        Checks.nonNegative(value, "millimetres");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Millimetres of(double value) {
        return new Millimetres(value);
    }

    @Override
    public int compareTo(Millimetres other) {
        return Double.compare(value, other.value);
    }
}
