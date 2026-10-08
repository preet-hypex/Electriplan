package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A protective device on the switchboard. */
public enum DeviceKind {
    @JsonProperty("RCBO") RCBO,
    @JsonProperty("RCD") RCD,
    @JsonProperty("MCB") MCB
}
