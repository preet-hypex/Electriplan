package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.projects.domain.FloorPlanState;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * A house's floor plan as the editor opens it: the draft if there is one,
 * otherwise the newest version.
 *
 * @param versionNo which version this is (the draft has the next number)
 * @param version send it back when saving the draft: a draft saved by someone
 *        else since is refused (409) rather than overwritten
 * @param basedOnVersionNo the version this draft started from, if any
 */
public record FloorPlanDocument(
        UUID houseId,
        UUID levelId,
        int versionNo,
        FloorPlanState state,
        int version,
        @Nullable Integer basedOnVersionNo,
        @Schema(description = "The FloorPlan (contracts/floor-plan.schema.json).", type = "object")
        JsonNode document,
        Instant savedAt,
        @Nullable UUID savedBy) {
}
