package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What an electrical point is. */
public enum PointKind {
    @JsonProperty("downlight") DOWNLIGHT,
    @JsonProperty("oyster") OYSTER,
    @JsonProperty("batten") BATTEN,
    @JsonProperty("pendant") PENDANT,
    @JsonProperty("wall-light") WALL_LIGHT,
    @JsonProperty("exterior-light") EXTERIOR_LIGHT,
    @JsonProperty("exhaust-fan") EXHAUST_FAN,
    @JsonProperty("ceiling-fan") CEILING_FAN,
    @JsonProperty("smoke-alarm") SMOKE_ALARM,
    @JsonProperty("switch") SWITCH,
    @JsonProperty("dimmer") DIMMER,
    @JsonProperty("gpo-single") GPO_SINGLE,
    @JsonProperty("gpo-double") GPO_DOUBLE,
    @JsonProperty("gpo-weatherproof") GPO_WEATHERPROOF,
    @JsonProperty("appliance-outlet") APPLIANCE_OUTLET,
    @JsonProperty("isolator") ISOLATOR,
    @JsonProperty("data") DATA,
    @JsonProperty("tv") TV
}
