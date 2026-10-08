package com.hypex.electriplan.tenancy.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.tenancy.dao.InvitationRepository;
import com.hypex.electriplan.tenancy.dao.MembershipRepository;
import com.hypex.electriplan.tenancy.domain.MemberRole;
import com.hypex.electriplan.tenancy.domain.MemberStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Seats in use, counted through Hibernate by the same rule the database
 * enforces (electriplan.seats_in_use, V3): active members and pending
 * invitations in any role but viewer. CompanyContextPostgresTests checks the
 * two agree.
 */
@Component
@RequiredArgsConstructor
public class Seats {

    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final Clock clock = Clock.systemUTC();

    public long inUse(UUID organisation) {
        Instant now = clock.instant();
        return memberships.countByIdOrganisationIdAndStatusAndRoleNot(organisation, MemberStatus.ACTIVE, MemberRole.VIEWER)
                + invitations.countByOrganisationIdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfterAndRoleNot(
                        organisation, now, MemberRole.VIEWER);
    }
}
