package com.hypex.electriplan.tenancy;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Companies, through Hibernate. Row-level security shows only the current company (and the caller's own, for the switcher). */
interface OrganisationRepository extends JpaRepository<OrganisationEntity, UUID> {
}
