package com.hypex.electriplan.tenancy;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Where a company's licence stands. A closed company is never resolved; suspended is read-only (T9). */
public enum LicenceStatus {
    @JsonProperty("trial") TRIAL,
    @JsonProperty("active") ACTIVE,
    @JsonProperty("suspended") SUSPENDED,
    @JsonProperty("closed") CLOSED;

    String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
