package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** How a switch gang is wired. */
public enum SwitchWay {
    @JsonProperty("1-way") ONE_WAY,
    @JsonProperty("2-way") TWO_WAY,
    @JsonProperty("intermediate") INTERMEDIATE
}
