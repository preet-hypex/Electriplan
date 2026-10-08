package com.hypex.electriplan.tenancy;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The request's tenant state, for the transaction manager to apply to each
 * transaction: who is acting, and (for company-scoped endpoints) in which
 * company. One per request thread; always cleared when the request ends.
 */
final class TenantSession {

    private record State(@Nullable UUID actorId, @Nullable CompanyContext company) {
    }

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private TenantSession() {
    }

    static void actor(UUID actorId) {
        CURRENT.set(new State(actorId, null));
    }

    static void company(CompanyContext company) {
        CURRENT.set(new State(company.userId(), company));
    }

    static @Nullable UUID actorId() {
        State s = CURRENT.get();
        return s == null ? null : s.actorId();
    }

    static @Nullable CompanyContext company() {
        State s = CURRENT.get();
        return s == null ? null : s.company();
    }

    static void clear() {
        CURRENT.remove();
    }
}
