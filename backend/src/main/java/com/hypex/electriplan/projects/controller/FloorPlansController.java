package com.hypex.electriplan.projects.controller;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.projects.dto.CommitForm;
import com.hypex.electriplan.projects.dto.ErrorMessage;
import com.hypex.electriplan.projects.dto.FloorPlanDocument;
import com.hypex.electriplan.projects.dto.FloorPlanVersion;
import com.hypex.electriplan.projects.dto.RestoreForm;
import com.hypex.electriplan.projects.dto.SaveDraftForm;
import com.hypex.electriplan.projects.dto.ValidationProblem;
import com.hypex.electriplan.projects.service.FloorPlansService;
import com.hypex.electriplan.tenancy.domain.Permission;
import com.hypex.electriplan.tenancy.domain.RequiresPermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A house's floor plan: the draft the editor saves as it goes, and its versions (P4). */
@RestController
@RequestMapping("/api/houses/{id}/floor-plan")
@RequiredArgsConstructor
@Tag(name = "Floor plans", description = "A house's floor plan: the draft being edited, and the versions saved from it.")
public class FloorPlansController {

    private final FloorPlansService floorPlans;

    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "OK")
    @interface Ok {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "404", description = "No such house in this company, or no floor plan (version) yet.",
            content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    @interface NotFound {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "400", description = "Not a valid floor plan, or a field is wrong; `errors` names each problem.",
            content = @Content(schema = @Schema(implementation = ValidationProblem.class)))
    @interface Invalid {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "409", description = "Saved by someone else since `version`, or the house is archived.",
            content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    @interface Conflict {
    }

    @GetMapping
    @RequiresPermission(Permission.COMPANY_VIEW)
    @Ok
    @NotFound
    @Operation(operationId = "openFloorPlan", summary = "The house's floor plan, to edit",
            description = "The draft if there is one, otherwise the newest version. 404 when the house has none yet.")
    FloorPlanDocument open(@PathVariable UUID id) {
        return floorPlans.open(id);
    }

    @PutMapping("/draft")
    @RequiresPermission(Permission.FLOOR_PLAN_EDIT)
    @Ok
    @Invalid
    @NotFound
    @Conflict
    @Operation(operationId = "saveFloorPlanDraft", summary = "Save the floor plan as the house's draft",
            description = "What the editor sends as it goes. Send the draft's `version` from the last open or save; leave it out "
                    + "when there is no draft yet. The plan is checked against contracts/floor-plan.schema.json.")
    FloorPlanDocument saveDraft(@PathVariable UUID id, @Valid @RequestBody SaveDraftForm form) {
        return floorPlans.saveDraft(id, form);
    }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.FLOOR_PLAN_EDIT)
    @Invalid
    @NotFound
    @Conflict
    @Operation(operationId = "saveFloorPlanVersion", summary = "Save the draft as a version",
            description = "Freezes the draft: read-only from now on, and the house's floor plan for designs. The next edit starts a new draft.")
    FloorPlanVersion commit(@PathVariable UUID id, @Valid @RequestBody CommitForm form) {
        return floorPlans.commit(id, form);
    }

    @GetMapping("/versions")
    @RequiresPermission(Permission.COMPANY_VIEW)
    @Ok
    @NotFound
    @Operation(operationId = "listFloorPlanVersions", summary = "The house's floor-plan history, newest first")
    List<FloorPlanVersion> history(@PathVariable UUID id) {
        return floorPlans.history(id);
    }

    @GetMapping("/versions/{versionNo}")
    @RequiresPermission(Permission.COMPANY_VIEW)
    @Ok
    @NotFound
    @Operation(operationId = "getFloorPlanVersion", summary = "One floor-plan version, to look at")
    FloorPlanDocument version(@PathVariable UUID id, @PathVariable int versionNo) {
        return floorPlans.version(id, versionNo);
    }

    @PostMapping("/versions/{versionNo}/restore")
    @RequiresPermission(Permission.FLOOR_PLAN_EDIT)
    @Ok
    @NotFound
    @Conflict
    @Operation(operationId = "restoreFloorPlanVersion", summary = "Make an earlier version the draft",
            description = "Its contents replace the draft (send the draft's `version` when there is one).")
    FloorPlanDocument restore(@PathVariable UUID id, @PathVariable int versionNo, @RequestBody RestoreForm form) {
        return floorPlans.restore(id, versionNo, form);
    }
}
