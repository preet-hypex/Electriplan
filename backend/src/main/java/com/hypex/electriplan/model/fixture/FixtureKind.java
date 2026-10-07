package com.hypex.electriplan.model.fixture;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What a fixture is. Wet fixtures set wet-area zones; benches and appliances set outlets and dedicated circuits. */
public enum FixtureKind {
    @JsonProperty("shower") SHOWER,
    @JsonProperty("bath") BATH,
    @JsonProperty("basin") BASIN,
    @JsonProperty("wc") WC,
    @JsonProperty("kitchen-sink") KITCHEN_SINK,
    @JsonProperty("laundry-tub") LAUNDRY_TUB,
    @JsonProperty("shower-screen") SHOWER_SCREEN,
    @JsonProperty("bench-run") BENCH_RUN,
    @JsonProperty("island-bench") ISLAND_BENCH,
    @JsonProperty("cooktop") COOKTOP,
    @JsonProperty("oven") OVEN,
    @JsonProperty("rangehood") RANGEHOOD,
    @JsonProperty("fridge-space") FRIDGE_SPACE,
    @JsonProperty("dishwasher") DISHWASHER,
    @JsonProperty("washing-machine") WASHING_MACHINE,
    @JsonProperty("dryer") DRYER,
    @JsonProperty("hot-water-unit") HOT_WATER_UNIT,
    @JsonProperty("ac-indoor-unit") AC_INDOOR_UNIT,
    @JsonProperty("ac-outdoor-unit") AC_OUTDOOR_UNIT,
    @JsonProperty("meter-box") METER_BOX,
    @JsonProperty("switchboard") SWITCHBOARD,
    @JsonProperty("pool") POOL
}
