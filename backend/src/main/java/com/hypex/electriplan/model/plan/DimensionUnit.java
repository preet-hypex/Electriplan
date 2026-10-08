package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The unit a dimension was written in on the plan. */
public enum DimensionUnit {
    @JsonProperty("mm") MM,
    @JsonProperty("m") M
}
