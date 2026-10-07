package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonProperty;

/** How the house heats water. */
public enum HotWaterType {
    @JsonProperty("heat-pump") HEAT_PUMP,
    @JsonProperty("electric-storage") ELECTRIC_STORAGE,
    @JsonProperty("electric-instantaneous") ELECTRIC_INSTANTANEOUS,
    @JsonProperty("gas") GAS,
    @JsonProperty("solar-boosted") SOLAR_BOOSTED
}
