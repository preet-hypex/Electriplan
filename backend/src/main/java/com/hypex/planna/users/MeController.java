package com.hypex.planna.users;

import java.time.Instant;
import java.util.UUID;
import com.hypex.planna.security.AuthenticatedUser;
import com.hypex.planna.security.AuthenticatedUsers;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
class MeController {

    private final AuthenticatedUsers callers;
    private final SupabaseUsers users;

    MeController(AuthenticatedUsers callers, SupabaseUsers users) {
        this.callers = callers;
        this.users = users;
    }

    @GetMapping
    Me me() {
        AuthenticatedUser caller = callers.require();
        return new Me(caller.id(), caller.email(), caller.tokenExpiresAt(), users.find(caller.id()).orElse(null));
    }

    record Me(UUID id, @Nullable String email, @Nullable Instant tokenExpiresAt, @Nullable SupabaseUser copy) {
    }
}
