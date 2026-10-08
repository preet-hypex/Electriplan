package com.hypex.electriplan.tenancy.dao;

import java.util.UUID;

import com.hypex.electriplan.tenancy.entity.OrganisationEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/** Companies, through Hibernate. Row-level security shows only the current company (and the caller's own, for the switcher). */
public interface OrganisationRepository extends JpaRepository<OrganisationEntity, UUID> {
}
