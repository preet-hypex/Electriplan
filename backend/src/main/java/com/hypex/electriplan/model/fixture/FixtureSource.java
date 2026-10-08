package com.hypex.electriplan.model.fixture;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Placed by a person, or detected from the plan image (and to be confirmed). */
public enum FixtureSource {
    @JsonProperty("manual") MANUAL,
    @JsonProperty("vision") VISION
}
