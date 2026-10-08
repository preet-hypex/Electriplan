/**
 * Which company a request is for, and keeping it to that company.
 *
 * <p>For an endpoint marked {@link com.hypex.electriplan.tenancy.domain.CompanyScoped},
 * the company comes from the {@code X-Organisation-Id} header (or is the
 * caller's only company), the caller must be an active member of it, and it
 * must not be closed. Every database transaction in the request then runs with
 * {@code electriplan.organisation_id} and {@code electriplan.actor_id} set, so
 * row-level security limits every query to that company. See
 * documents/teams-and-licences-plan.md, story T3.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Tenancy", allowedDependencies = "security")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.tenancy;
