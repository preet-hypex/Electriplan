package com.hypex.electriplan.rules;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How the engine evaluates a rule (§6.9). The code implements kinds; the
 * numbers are the rule's parameters.
 */
public enum RuleKind {
    /** Item kinds may not be placed inside a zone kind. */
    @JsonProperty("exclusion") EXCLUSION,
    /** An item at least a distance from a fixture, item or corner. */
    @JsonProperty("clearance") CLEARANCE,
    /** At least n per room, area or length. */
    @JsonProperty("count_per") COUNT_PER,
    /** A parameter, such as a mounting height. */
    @JsonProperty("value") VALUE,
    /** A lookup table, with its interpolation rule. */
    @JsonProperty("table") TABLE,
    /** A computed value at most a limit. */
    @JsonProperty("limit") LIMIT,
    /** If A then B. */
    @JsonProperty("requires") REQUIRES;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
