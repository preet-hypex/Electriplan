package com.hypex.electriplan.users.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminUser(
        UUID id,
        @Nullable String email,
        @Nullable String phone,
        @JsonProperty("user_metadata") @Nullable Map<String, Object> userMetadata,
        @JsonProperty("app_metadata") @Nullable Map<String, Object> appMetadata,
        @JsonProperty("email_confirmed_at") @Nullable Instant emailConfirmedAt,
        @JsonProperty("invited_at") @Nullable Instant invitedAt,
        @JsonProperty("last_sign_in_at") @Nullable Instant lastSignInAt,
        @JsonProperty("banned_until") @Nullable Instant bannedUntil,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") @Nullable Instant updatedAt) {

    public AdminUser {
        email = blankToNull(email);
        phone = blankToNull(phone);
        userMetadata = userMetadata == null ? Map.of() : userMetadata;
        appMetadata = appMetadata == null ? Map.of() : appMetadata;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page(@Nullable List<AdminUser> users) {
    }
}
