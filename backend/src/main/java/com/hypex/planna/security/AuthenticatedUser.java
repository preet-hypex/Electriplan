package com.hypex.planna.security;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record AuthenticatedUser(UUID id, @Nullable String email, @Nullable Instant tokenExpiresAt) {
}
