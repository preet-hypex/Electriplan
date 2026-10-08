package com.hypex.electriplan.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.projects.domain.DwellingType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** A new house, or (with {@code version}) an existing one's details. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HouseForm(
        @NotBlank(message = "Give the house a name")
        @Size(max = 200, message = "Keep the name under 200 characters") @Nullable String name,
        @Nullable DwellingType dwellingType,
        @Nullable Integer version) {
}
