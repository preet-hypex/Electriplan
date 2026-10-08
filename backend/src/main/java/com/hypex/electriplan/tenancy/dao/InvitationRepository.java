package com.hypex.electriplan.tenancy.dao;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.tenancy.domain.MemberRole;
import com.hypex.electriplan.tenancy.entity.InvitationEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {

    long countByOrganisationIdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterAndRoleNot(
            UUID organisationId, Instant now, MemberRole notRole);
}
