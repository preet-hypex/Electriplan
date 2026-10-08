package com.hypex.electriplan.model.units;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/** A power in kilowatts, never negative (appliance nameplates). Written to JSON as a plain number. */
public record Kilowatts(@JsonValue double value) implements Comparable<Kilowatts> {

    public Kilowatts {
        Checks.nonNegative(value, "kilowatts");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Kilowatts of(double value) {
        return new Kilowatts(value);
    }

    @Override
    public int compareTo(Kilowatts other) {
        return Double.compare(value, other.value);
    }
}
