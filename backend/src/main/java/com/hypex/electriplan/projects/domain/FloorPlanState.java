package com.hypex.electriplan.projects.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A floor-plan version: the one editable draft, or a committed (read-only) version. */
public enum FloorPlanState {
    @JsonProperty("draft") DRAFT,
    @JsonProperty("committed") COMMITTED;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
