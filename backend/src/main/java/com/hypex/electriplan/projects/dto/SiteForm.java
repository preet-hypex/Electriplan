package com.hypex.electriplan.projects.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.common.AustralianState;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Where the job is. The state decides which rules design its houses. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SiteForm(
        @Size(max = 200, message = "Keep the street under 200 characters") @Nullable String street,
        @Size(max = 100, message = "Keep the suburb under 100 characters") @Nullable String suburb,
        @NotNull(message = "Choose the state the site is in") @Nullable AustralianState state,
        @Pattern(regexp = "^[0-9]{4}$", message = "A postcode is 4 digits") @Nullable String postcode) {
}
