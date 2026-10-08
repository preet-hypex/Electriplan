package com.hypex.electriplan.projects.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Where a project stands as a job. Each house has its own, finer stage. */
public enum ProjectStatus {
    @JsonProperty("active") ACTIVE,
    @JsonProperty("on_hold") ON_HOLD,
    @JsonProperty("completed") COMPLETED,
    @JsonProperty("cancelled") CANCELLED;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
