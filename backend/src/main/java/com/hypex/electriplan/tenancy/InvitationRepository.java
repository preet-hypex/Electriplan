package com.hypex.electriplan.tenancy;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    long countByOrganisationIdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterAndRoleNot(
            UUID organisationId, Instant now, MemberRole notRole);
}
