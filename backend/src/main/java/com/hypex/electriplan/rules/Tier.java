package com.hypex.electriplan.rules;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The three tiers of rule, kept apart (documents/electrical-engine-plan.md §2.4). */
public enum Tier {
    /** The Wiring Rules, NCC and state law. A violation is an error and blocks export. */
    @JsonProperty("mandatory") MANDATORY,
    /** State variations and distributor service rules. Only in state files. */
    @JsonProperty("regulatory") REGULATORY,
    /** Good practice and company preference. A violation is a warning; companies may change the values. */
    @JsonProperty("policy") POLICY;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
