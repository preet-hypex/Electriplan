package com.hypex.electriplan.users.controller;

import com.hypex.electriplan.security.AuthenticatedUser;
import com.hypex.electriplan.security.AuthenticatedUsers;
import com.hypex.electriplan.users.dto.Me;
import com.hypex.electriplan.users.service.SupabaseUsers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Me", description = "The signed-in user.")
@RequestMapping("/api/me")
public class MeController {

    private final AuthenticatedUsers callers;
    private final SupabaseUsers users;

    MeController(AuthenticatedUsers callers, SupabaseUsers users) {
        this.callers = callers;
        this.users = users;
    }

    @GetMapping
    @Operation(operationId = "getMe", summary = "The signed-in user",
            description = "Who the access token is for, and the API's copy of their Supabase account (null until the next sync).")
    Me me() {
        AuthenticatedUser caller = callers.require();
        return new Me(caller.id(), caller.email(), caller.tokenExpiresAt(), users.find(caller.id()).orElse(null));
    }

}
