package com.hypex.electriplan.projects.dto;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

public record Level(UUID id, String name, int ordinal, int ceilingHeightMm, @Nullable UUID currentFloorPlanVersionId) {
}
