package com.hypex.electriplan.tenancy;

import java.util.Optional;

import org.springframework.stereotype.Component;

/** The company the current request acts in. Only set inside a {@link CompanyScoped} endpoint. */
@Component
public class CurrentCompany {

    public Optional<CompanyContext> get() {
        return Optional.ofNullable(TenantSession.company());
    }

    public CompanyContext require() {
        return get().orElseThrow(() -> new IllegalStateException(
                "No company for this request: mark the endpoint @CompanyScoped"));
    }
}
