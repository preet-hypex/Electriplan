package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What a circuit supplies. */
public enum CircuitType {
    @JsonProperty("lighting") LIGHTING,
    @JsonProperty("power") POWER,
    @JsonProperty("dedicated") DEDICATED,
    @JsonProperty("smoke-alarm") SMOKE_ALARM
}
