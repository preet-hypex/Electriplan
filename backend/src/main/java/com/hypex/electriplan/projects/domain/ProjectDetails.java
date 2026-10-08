package com.hypex.electriplan.projects.domain;

import java.time.LocalDate;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.projects.entity.ProjectEntity;
import com.hypex.electriplan.projects.service.ProjectDetailsCheck;

import org.jspecify.annotations.Nullable;

/**
 * A project's details once checked and tidied (blank text is no text): what
 * {@link ProjectEntity#changeDetails} applies. Made by {@link ProjectDetailsCheck}.
 *
 * @param status null keeps the project's current status
 */
public record ProjectDetails(
        String name,
        @Nullable String description,
        @Nullable String lotNumber,
        @Nullable String street,
        @Nullable String suburb,
        AustralianState state,
        @Nullable String postcode,
        @Nullable String distributor,
        int supplyPhases,
        @Nullable LocalDate dueOn,
        @Nullable ProjectStatus status) {
}
