package com.hypex.electriplan.projects.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.projects.domain.ProjectStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** A new project, or (with {@code version}) the whole of an existing one's details. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectForm(
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
