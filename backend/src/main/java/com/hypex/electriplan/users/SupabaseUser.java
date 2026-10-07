package com.hypex.electriplan.users;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record SupabaseUser(
        UUID id,
        @Nullable String email,
        @Nullable String phone,
        Map<String, Object> userMetadata,
        Map<String, Object> appMetadata,
        @Nullable Instant emailConfirmedAt,
        @Nullable Instant invitedAt,
        @Nullable Instant lastSignInAt,
        @Nullable Instant bannedUntil,
        Instant createdAt,
        @Nullable Instant updatedAt,
        Instant copiedAt) {
}
