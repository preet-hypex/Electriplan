package com.hypex.electriplan.projects;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.common.AustralianState;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * What the projects endpoints read and write. Request records leave out
 * nulls ({@code @JsonInclude(NON_NULL)}), which also marks their optional
 * fields optional in the OpenAPI document.
 */
final class ProjectsApi {

    private ProjectsApi() {
    }

    // ---- Requests ----

    /** Where the job is. The state decides which rules design its houses. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SiteForm(
            @Size(max = 200, message = "Keep the street under 200 characters") @Nullable String street,
            @Size(max = 100, message = "Keep the suburb under 100 characters") @Nullable String suburb,
            @NotNull(message = "Choose the state the site is in") @Nullable AustralianState state,
            @Pattern(regexp = "^[0-9]{4}$", message = "A postcode is 4 digits") @Nullable String postcode) {
    }

    /** A new project, or (with {@code version}) the whole of an existing one's details. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProjectForm(
            @NotBlank(message = "Give the project a name")
            @Size(max = 200, message = "Keep the name under 200 characters") @Nullable String name,
            @Size(max = 2000, message = "Keep the description under 2000 characters") @Nullable String description,
            @Size(max = 40, message = "Keep the lot number under 40 characters") @Nullable String lotNumber,
            @NotNull(message = "Give the site's address, at least its state") @Valid @Nullable SiteForm site,
            @Nullable String distributor,
            @Nullable Integer supplyPhases,
            @Nullable LocalDate dueOn,
            @Nullable ProjectStatus status,
            @Nullable Integer version) {
    }

    /** A new house, or (with {@code version}) an existing one's details. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HouseForm(
            @NotBlank(message = "Give the house a name")
            @Size(max = 200, message = "Keep the name under 200 characters") @Nullable String name,
            @Nullable DwellingType dwellingType,
            @Nullable Integer version) {
    }

    // ---- Responses ----

    record Site(@Nullable String street, @Nullable String suburb, AustralianState state, @Nullable String postcode) {
    }

    record StageCount(HouseStage stage, long count) {
    }

    /** A row of the project list. */
    record ProjectSummary(UUID id, String reference, String name, @Nullable String suburb, AustralianState state,
                          ProjectStatus status, boolean archived, long houseCount, List<StageCount> stages,
                          Instant lastActivityAt) {
    }

    record ProjectPage(List<ProjectSummary> items, int page, int size, long total) {
    }

    record HouseSummary(UUID id, String name, DwellingType dwellingType, HouseStage stage, boolean archived,
                        Instant updatedAt) {
    }

    /**
     * @param version send it back when changing the project: a change made
     *        since you read it is refused (409) rather than overwritten
     */
    record Project(UUID id, String reference, String name, @Nullable String description, ProjectStatus status,
                   @Nullable String lotNumber, Site site, @Nullable String distributor, int supplyPhases,
                   @Nullable LocalDate dueOn, boolean archived, @Nullable Instant archivedAt, Instant createdAt,
                   Instant lastActivityAt, int version, List<HouseSummary> houses) {
    }

    record ProjectRef(UUID id, String reference, String name) {
    }

    record Level(UUID id, String name, int ordinal, int ceilingHeightMm, @Nullable UUID currentFloorPlanVersionId) {
    }

    record House(UUID id, ProjectRef project, String name, DwellingType dwellingType, int storeys, HouseStage stage,
                 Instant stageChangedAt, List<Level> levels, boolean archived, @Nullable Instant archivedAt,
                 Instant createdAt, Instant updatedAt, int version) {
    }

    // ---- Problems ----

    record FieldProblem(String field, String message) {
    }

    /** A 400 that names the fields to fix. */
    record ValidationProblem(String message, List<FieldProblem> errors) {
    }

    /** The same {"message"} body as every other refusal (tenancy's ErrorMessage). */
    @io.swagger.v3.oas.annotations.media.Schema(name = "ErrorMessage")
    record ErrorBody(String message) {
    }
}
