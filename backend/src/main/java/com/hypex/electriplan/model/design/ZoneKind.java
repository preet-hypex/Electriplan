package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A keep-out zone around a fixture (AS/NZS 3000 section 6). */
public enum ZoneKind {
    @JsonProperty("bath-zone-0") BATH_ZONE_0,
    @JsonProperty("bath-zone-1") BATH_ZONE_1,
    @JsonProperty("bath-zone-2") BATH_ZONE_2,
    @JsonProperty("bath-zone-3") BATH_ZONE_3,
    @JsonProperty("basin-zone") BASIN_ZONE,
    @JsonProperty("sink-zone") SINK_ZONE,
    @JsonProperty("tub-zone") TUB_ZONE,
    @JsonProperty("pool-zone-0") POOL_ZONE_0,
    @JsonProperty("pool-zone-1") POOL_ZONE_1,
    @JsonProperty("pool-zone-2") POOL_ZONE_2
}
