package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.DwellingType;
import com.hypex.electriplan.projects.domain.HouseStage;

import org.jspecify.annotations.Nullable;

public record House(UUID id, ProjectRef project, String name, DwellingType dwellingType, int storeys, HouseStage stage,
             Instant stageChangedAt, List<Level> levels, boolean archived, @Nullable Instant archivedAt,
             Instant createdAt, Instant updatedAt, int version) {
}
