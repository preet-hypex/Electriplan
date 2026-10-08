package com.hypex.electriplan.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * Makes an earlier version the draft again.
 *
 * @param version the current draft's version, when there is a draft: restoring
 *        replaces it, so it must be the one the caller has seen
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RestoreForm(@Nullable Integer version) {
}
