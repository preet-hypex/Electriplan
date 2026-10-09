package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.projects.domain.FloorPlanState;
import com.hypex.electriplan.projects.domain.HouseStage;

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
 * @param houseStage where the house is now (saving a plan can move it, e.g. to floor_plan_review)
 * @param unsavedChanges a draft that differs from the version it started from (or started
 *        from nothing): work that is not saved as a version yet. Always false for a version
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
        @Nullable UUID savedBy,
        HouseStage houseStage,
        boolean unsavedChanges) {
}
