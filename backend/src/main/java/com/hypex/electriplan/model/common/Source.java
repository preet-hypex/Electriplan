package com.hypex.electriplan.model.common;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Who placed an item. The engine never moves a {@link #MANUAL} item; it designs around it. */
public enum Source {
    @JsonProperty("engine") ENGINE,
    @JsonProperty("manual") MANUAL
}
