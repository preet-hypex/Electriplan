package com.hypex.electriplan.model.brief;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.hypex.electriplan.model.Checks;

/**
 * An electricity distributor (DNSP), by code: {@code jemena}, {@code citipower}.
 *
 * <p>Configurable reference data rather than an enum: distributors are rows of
 * {@code electriplan.electricity_distributor}, added without a code change.
 * This type only guarantees the code is well formed (the same format as that
 * column); whether it exists, and in which state, is checked against the table
 * by the reference module. Written to JSON as a plain string.
 */
public record DistributorCode(@JsonValue String value) {

    public static final Pattern FORMAT = Pattern.compile("^[a-z0-9_]{1,40}$");

    public DistributorCode {
        Checks.matches(value, FORMAT, "distributor");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DistributorCode of(String value) {
        return new DistributorCode(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
