package com.hypex.electriplan.users.dto;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.users.entity.SupabaseUser;

import org.jspecify.annotations.Nullable;

public record Me(UUID id, @Nullable String email, @Nullable Instant tokenExpiresAt, @Nullable SupabaseUser copy) {
}
