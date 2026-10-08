package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonProperty;

/** How a door opens. Only a swinging door has a hinge and a swing; a slider or garage door changes the switch rules. */
public enum DoorStyle {
    @JsonProperty("swing") SWING,
    @JsonProperty("sliding") SLIDING,
    @JsonProperty("garage") GARAGE
}
