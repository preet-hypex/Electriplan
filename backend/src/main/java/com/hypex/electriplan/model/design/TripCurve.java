package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A circuit breaker trip curve. */
public enum TripCurve {
    @JsonProperty("B") B,
    @JsonProperty("C") C,
    @JsonProperty("D") D
}
