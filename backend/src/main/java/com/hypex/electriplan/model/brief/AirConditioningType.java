package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The air conditioning the house will have. */
public enum AirConditioningType {
    @JsonProperty("ducted") DUCTED,
    @JsonProperty("split") SPLIT,
    @JsonProperty("none") NONE
}
