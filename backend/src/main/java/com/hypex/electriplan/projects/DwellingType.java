package com.hypex.electriplan.projects;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What kind of dwelling a house design is. */
public enum DwellingType {
    @JsonProperty("house") HOUSE,
    @JsonProperty("townhouse") TOWNHOUSE,
    @JsonProperty("unit") UNIT,
    @JsonProperty("granny_flat") GRANNY_FLAT,
    @JsonProperty("extension") EXTENSION,
    @JsonProperty("other") OTHER;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
