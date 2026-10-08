package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.HouseStage;

/** A house someone worked on lately, with the project it belongs to: "continue where you left off". */
public record RecentHouse(UUID id, String name, HouseStage stage, Instant updatedAt, ProjectRef project) {
}
