package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.AustralianState;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * What the floor plan cannot say about a house but its electrical design needs.
 * Follows {@code contracts/project-brief.schema.json}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record ProjectBrief(
        Integer version,
        AustralianState state,
        Distributor distributor,
        Supply supply,
        Construction construction,
        Appliances appliances,
        @Nullable Preferences preferences) {

    public static final int VERSION = 1;

    public ProjectBrief {
        Checks.oneOf(version, "version", VERSION);
        Checks.required(state, "state");
        Checks.required(distributor, "distributor");
        Checks.required(supply, "supply");
        Checks.required(construction, "construction");
        Checks.required(appliances, "appliances");
    }

    /** A builder with the current version already set. */
    public static ProjectBriefBuilder builder() {
        return new ProjectBriefBuilder().version(VERSION);
    }
}
