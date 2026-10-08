package com.hypex.electriplan.projects;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Where a house is in its journey from floor plan to quote: the codes of
 * electriplan.plan_stage. The moves allowed between them are data
 * (plan_stage_transition), enforced by the database.
 */
public enum HouseStage {
    @JsonProperty("awaiting_upload") AWAITING_UPLOAD,
    @JsonProperty("analysing") ANALYSING,
    @JsonProperty("floor_plan_review") FLOOR_PLAN_REVIEW,
    @JsonProperty("floor_plan_approved") FLOOR_PLAN_APPROVED,
    @JsonProperty("electrical_design") ELECTRICAL_DESIGN,
    @JsonProperty("electrical_review") ELECTRICAL_REVIEW,
    @JsonProperty("changes_requested") CHANGES_REQUESTED,
    @JsonProperty("design_approved") DESIGN_APPROVED,
    @JsonProperty("quoting") QUOTING,
    @JsonProperty("quote_sent") QUOTE_SENT,
    @JsonProperty("won") WON,
    @JsonProperty("lost") LOST,
    @JsonProperty("on_hold") ON_HOLD,
    @JsonProperty("archived") ARCHIVED;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
