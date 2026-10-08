package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.DwellingType;
import com.hypex.electriplan.projects.domain.HouseStage;

public record HouseSummary(UUID id, String name, DwellingType dwellingType, HouseStage stage, boolean archived,
                    Instant updatedAt) {
}
