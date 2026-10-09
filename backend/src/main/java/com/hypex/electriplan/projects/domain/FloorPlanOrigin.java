package com.hypex.electriplan.projects.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Where a floor-plan version came from. (From an analysis run arrives with P5, which records the run.) */
public enum FloorPlanOrigin {
    @JsonProperty("analysis") ANALYSIS,
    @JsonProperty("editor") EDITOR,
    @JsonProperty("import") IMPORT;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
