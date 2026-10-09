package com.hypex.electriplan.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Freezes the draft as a version.
 *
 * @param version the draft's version, as last saved
 * @param note what changed, for the history ("Kitchen moved")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommitForm(
        @Nullable Integer version,
        @Size(max = 500, message = "Keep the note under 500 characters") @Nullable String note) {
}
