package com.hypex.electriplan.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Which face of a wall, looking from the wall's start towards its end. */
public enum Side {
    @JsonProperty("left") LEFT,
    @JsonProperty("right") RIGHT
}
