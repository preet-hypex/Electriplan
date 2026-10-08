package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.ProjectStatus;

import org.jspecify.annotations.Nullable;

/**
 * @param version send it back when changing the project: a change made
 *        since you read it is refused (409) rather than overwritten
 */
public record Project(UUID id, String reference, String name, @Nullable String description, ProjectStatus status,
               @Nullable String lotNumber, Site site, @Nullable String distributor, int supplyPhases,
               @Nullable LocalDate dueOn, boolean archived, @Nullable Instant archivedAt, Instant createdAt,
               Instant lastActivityAt, int version, List<HouseSummary> houses) {
}
