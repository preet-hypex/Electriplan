package com.hypex.electriplan.tenancy.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A person's role in a company. Owners, admins, builders and electricians use a seat; viewers do not. */
public enum MemberRole {
    @JsonProperty("owner") OWNER,
    @JsonProperty("admin") ADMIN,
    @JsonProperty("builder") BUILDER,
    @JsonProperty("electrician") ELECTRICIAN,
    @JsonProperty("viewer") VIEWER;

    /** As stored in electriplan.organisation_member.role. */
    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
