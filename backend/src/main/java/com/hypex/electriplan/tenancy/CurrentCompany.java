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

    /** Whether the caller's role in the current company has a permission. */
    public boolean can(Permission permission) {
        return PermissionMatrix.allows(require().role(), permission);
    }

    /**
     * Refuses (403) unless the caller has the permission: for checks inside a
     * service, where the permission depends on the data, not just the endpoint.
     */
    public void require(Permission permission) {
        CompanyContext company = require();
        if (!PermissionMatrix.allows(company.role(), permission)) {
            throw CompanyContextInterceptor.refusal(company.role(), permission);
        }
    }
}
