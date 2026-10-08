package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The cooktop the house will have. */
public enum CooktopType {
    @JsonProperty("induction") INDUCTION,
    @JsonProperty("electric") ELECTRIC,
    @JsonProperty("gas") GAS,
    @JsonProperty("none") NONE
}
