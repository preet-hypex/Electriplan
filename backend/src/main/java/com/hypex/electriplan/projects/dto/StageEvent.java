package com.hypex.electriplan.projects.dto;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.HouseStage;

import org.jspecify.annotations.Nullable;

/**
 * A house's stage changed.
 *
 * @param from the stage before; null for the house's first stage
 * @param byName the person's name or email, when known
 */
public record StageEvent(@Nullable HouseStage from, HouseStage to, Instant at, @Nullable UUID by, @Nullable String byName,
                         @Nullable String note) {
}
