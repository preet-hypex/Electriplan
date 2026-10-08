package com.hypex.electriplan.tenancy;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which company a request is for, from the caller's memberships and
 * the X-Organisation-Id header. Runs in its own read-only transaction with only
 * the actor set, so row-level security shows it the caller's own memberships
 * and nothing else.
 */
@Service
@RequiredArgsConstructor
class CompanyResolver {

    static final String HEADER = "X-Organisation-Id";

    private final MembershipRepository memberships;

    @Transactional(readOnly = true)
    public CompanyContext resolve(UUID actor, @Nullable String header) {
        List<MemberEntity> usable = memberships.usableBy(actor);

        if (header != null && !header.isBlank()) {
            UUID requested = parse(header.strip());
            // The same answer whether the company does not exist, is closed, or
            // the caller is not (or no longer) an active member of it: nothing
            // to learn about other companies from the response.
            return usable.stream()
                    .filter(m -> m.getId().getOrganisationId().equals(requested))
                    .findFirst()
                    .map(m -> context(m, actor))
                    .orElseThrow(() -> new CompanyAccessException(HttpStatus.FORBIDDEN,
                            "You are not a member of that company, or it is closed."));
        }

        return switch (usable.size()) {
            case 0 -> throw new CompanyAccessException(HttpStatus.FORBIDDEN,
                    "You are not a member of any company yet. Ask a company owner or admin to invite you.");
            case 1 -> context(usable.getFirst(), actor);
            default -> throw new CompanyAccessException(HttpStatus.BAD_REQUEST,
                    "You belong to " + usable.size() + " companies: choose one with the " + HEADER + " header.");
        };
    }

    private static UUID parse(String header) {
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            throw new CompanyAccessException(HttpStatus.BAD_REQUEST, HEADER + " must be a company id (a UUID).");
        }
    }

    private static CompanyContext context(MemberEntity m, UUID actor) {
        return new CompanyContext(m.getId().getOrganisationId(), actor, m.getRole(), m.getOrganisation().getStatus());
    }
}
