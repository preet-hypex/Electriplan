package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The electricity distributor (DNSP). Same codes as the database table electricity_distributor. */
public enum Distributor {
    @JsonProperty("citipower") CITIPOWER,
    @JsonProperty("powercor") POWERCOR,
    @JsonProperty("jemena") JEMENA,
    @JsonProperty("united_energy") UNITED_ENERGY,
    @JsonProperty("ausnet_services") AUSNET_SERVICES
}
