package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A percentage from 0 to 100 (voltage drop, spare switchboard poles). Written to JSON as a plain number. */
public record Percent(@JsonValue double value) implements Comparable<Percent> {

    public Percent {
        Checks.nonNegative(value, "percent");
        Checks.between(value, 0, 100, "percent");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Percent of(double value) {
        return new Percent(value);
    }

    @Override
    public int compareTo(Percent other) {
        return Double.compare(value, other.value);
    }
}
