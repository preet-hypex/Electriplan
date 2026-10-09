package com.hypex.electriplan.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.projects.domain.FloorPlanOrigin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * The editor's plan, saved as the house's draft.
 *
 * @param version the draft's version from the last open or save; leave it
 *        out when there is no draft yet (the first save after opening a
 *        committed version, or a new plan)
 * @param origin where the plan came from (editor by default)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaveDraftForm(
        @NotNull(message = "Send the floor plan") @Schema(description = "The FloorPlan (contracts/floor-plan.schema.json).", type = "object")
        @Nullable JsonNode document,
        @Nullable Integer version,
        @Nullable FloorPlanOrigin origin) {
}
