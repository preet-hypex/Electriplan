package com.hypex.electriplan.projects.dto;

import org.jspecify.annotations.Nullable;

/**
 * An earlier version restored as the draft.
 *
 * @param draft the new draft, with the earlier version's contents
 * @param keptAsVersionNo when the draft that was there had changes not saved as
 *        a version and they were to be kept, they were saved first, as this
 *        version ("Before restoring version N"); null otherwise
 */
public record Restored(FloorPlanDocument draft, @Nullable Integer keptAsVersionNo) {
}
