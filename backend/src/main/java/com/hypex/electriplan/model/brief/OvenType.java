package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The oven the house will have. */
public enum OvenType {
    @JsonProperty("electric") ELECTRIC,
    @JsonProperty("gas") GAS,
    @JsonProperty("none") NONE
}
