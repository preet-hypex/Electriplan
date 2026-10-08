package com.hypex.electriplan.tenancy;

import java.util.UUID;

/** The company a request acts in, who is acting, their role there, and the company's licence status. */
public record CompanyContext(UUID organisationId, UUID userId, MemberRole role, LicenceStatus licence) {
}
