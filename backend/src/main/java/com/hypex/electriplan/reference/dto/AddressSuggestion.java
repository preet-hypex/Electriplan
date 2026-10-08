package com.hypex.electriplan.reference.dto;

import com.hypex.electriplan.model.common.AustralianState;

import org.jspecify.annotations.Nullable;

/**
 * An address the finder suggests while someone types a site address, split
 * into the project form's fields.
 *
 * @param label how it reads in the list: "12 Glenlyon Road, Brunswick VIC 3056"
 * @param street house number and street, as far as the source knows them
 */
public record AddressSuggestion(
        String label,
        @Nullable String street,
        @Nullable String suburb,
        AustralianState state,
        @Nullable String postcode,
        double latitude,
        double longitude) {
}
