package com.hypex.electriplan.projects;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.UUID;

import com.hypex.electriplan.tenancy.Permission;
import com.hypex.electriplan.tenancy.RequiresPermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A company's projects and their houses (documents/projects-workspace-plan.md, P1). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Projects", description = "A company's projects (a job at one site) and their houses.")
class ProjectsController {

    private final ProjectsService service;

    /** Declaring error responses stops springdoc adding the 200, so it is declared too (its body from the return type). */
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "OK")
    @interface Ok {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "400", description = "A field is missing or not valid; `errors` names each one.",
            content = @Content(schema = @Schema(implementation = ProjectsApi.ValidationProblem.class)))
    @interface Invalid {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "404", description = "No such project or house in this company.",
            content = @Content(schema = @Schema(implementation = ProjectsApi.ErrorBody.class)))
    @interface NotFound {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "409", description = "Changed by someone else since `version`, or archived.",
            content = @Content(schema = @Schema(implementation = ProjectsApi.ErrorBody.class)))
    @interface Conflict {
    }

    // ---- Projects ----

    @GetMapping("/projects")
    @RequiresPermission(Permission.COMPANY_VIEW)
    @Ok
    @Operation(operationId = "listProjects", summary = "The company's projects",
            description = "Newest activity first (a change to the project or any of its houses). Archived projects only with `archived=true`.")
    ProjectsApi.ProjectPage list(
            @Parameter(description = "Words in the name, reference, street or suburb.") @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable ProjectStatus status,
            @Parameter(description = "true: archived projects instead of current ones.") @RequestParam(defaultValue = "false") boolean archived,
            @Parameter(description = "From 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "1 to 100.") @RequestParam(defaultValue = "25") int size) {
        return service.list(q, status, archived, page, size);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.PROJECT_EDIT)
    @Invalid
    @Operation(operationId = "createProject", summary = "Start a project",
            description = "The reference (PRJ-000001...) is assigned in order. The state is required; a distributor must supply that state.")
    ProjectsApi.Project create(@Valid @RequestBody ProjectsApi.ProjectForm form) {
        return service.create(form);
    }

    @GetMapping("/projects/{id}")
    @RequiresPermission(Permission.COMPANY_VIEW)
    @NotFound
    @Ok
    @Operation(operationId = "getProject", summary = "A project with its houses")
    ProjectsApi.Project get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/projects/{id}")
    @RequiresPermission(Permission.PROJECT_EDIT)
    @Invalid
    @NotFound
    @Conflict
    @Ok
    @Operation(operationId = "updateProject", summary = "Change a project's details",
            description = "Send every field, and the `version` you read. A project changed since then is refused (409), not overwritten.")
    ProjectsApi.Project update(@PathVariable UUID id, @Valid @RequestBody ProjectsApi.ProjectForm form) {
        return service.update(id, form);
    }

    @PostMapping("/projects/{id}/archive")
    @RequiresPermission(Permission.PROJECT_EDIT)
    @NotFound
    @Ok
    @Operation(operationId = "archiveProject", summary = "Archive a project",
            description = "It leaves the project list and becomes read-only, with its houses, until restored.")
    ProjectsApi.Project archive(@PathVariable UUID id) {
        return service.archive(id, true);
    }

    @PostMapping("/projects/{id}/restore")
    @RequiresPermission(Permission.PROJECT_EDIT)
    @NotFound
    @Ok
    @Operation(operationId = "restoreProject", summary = "Restore an archived project")
    ProjectsApi.Project restore(@PathVariable UUID id) {
        return service.archive(id, false);
    }

    // ---- Houses ----

    @PostMapping("/projects/{id}/houses")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(Permission.PROJECT_EDIT)
    @Invalid
    @NotFound
    @Conflict
    @Operation(operationId = "addHouse", summary = "Add a house to a project",
            description = "It starts awaiting its floor plan, with its ground floor ready.")
    ProjectsApi.House addHouse(@PathVariable UUID id, @Valid @RequestBody ProjectsApi.HouseForm form) {
        return service.addHouse(id, form);
    }

    @GetMapping("/houses/{id}")
    @RequiresPermission(Permission.COMPANY_VIEW)
    @NotFound
    @Ok
    @Operation(operationId = "getHouse", summary = "A house, with its project and storeys")
    ProjectsApi.House getHouse(@PathVariable UUID id) {
        return service.getHouse(id);
    }

    @PutMapping("/houses/{id}")
    @RequiresPermission(Permission.PROJECT_EDIT)
    @Invalid
    @NotFound
    @Conflict
    @Ok
    @Operation(operationId = "updateHouse", summary = "Change a house's details",
            description = "Send the `version` you read; a house changed since is refused (409).")
    ProjectsApi.House updateHouse(@PathVariable UUID id, @Valid @RequestBody ProjectsApi.HouseForm form) {
        return service.updateHouse(id, form);
    }

    @PostMapping("/houses/{id}/archive")
    @RequiresPermission(Permission.PROJECT_EDIT)
    @NotFound
    @Ok
    @Operation(operationId = "archiveHouse", summary = "Archive a house")
    ProjectsApi.House archiveHouse(@PathVariable UUID id) {
        return service.archiveHouse(id, true);
    }

    @PostMapping("/houses/{id}/restore")
    @RequiresPermission(Permission.PROJECT_EDIT)
    @NotFound
    @Conflict
    @Ok
    @Operation(operationId = "restoreHouse", summary = "Restore an archived house")
    ProjectsApi.House restoreHouse(@PathVariable UUID id) {
        return service.archiveHouse(id, false);
    }
}
