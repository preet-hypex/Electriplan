package com.hypex.electriplan.model.plan;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What a piece of text on the plan is: a room's name, a dimension, or anything else. */
public enum LabelKind {
    @JsonProperty("room") ROOM,
    @JsonProperty("dimension") DIMENSION,
    @JsonProperty("other") OTHER
}
