package com.hypex.electriplan.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * Makes an earlier version the draft again.
 *
 * @param version the current draft's version, when there is a draft: restoring
 *        replaces it, so it must be the one the caller has seen
 * @param keepDraft what to do with a draft that has unsaved changes (see
 *        FloorPlanDocument.unsavedChanges): true saves it as a version first,
 *        false lets the restore replace it. Left out: kept, so nothing is lost
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestoreForm(@Nullable Integer version, @Nullable Boolean keepDraft) {
}
