package com.hypex.electriplan.projects.dto;

import com.hypex.electriplan.model.common.AustralianState;

import org.jspecify.annotations.Nullable;

public record Site(@Nullable String street, @Nullable String suburb, AustralianState state, @Nullable String postcode) {
}
