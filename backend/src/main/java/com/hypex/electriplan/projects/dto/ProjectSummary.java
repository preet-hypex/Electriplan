package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.projects.domain.ProjectStatus;

import org.jspecify.annotations.Nullable;

/** A row of the project list. */
public record ProjectSummary(UUID id, String reference, String name, @Nullable String suburb, AustralianState state,
                      ProjectStatus status, boolean archived, long houseCount, List<StageCount> stages,
                      Instant lastActivityAt) {
}
