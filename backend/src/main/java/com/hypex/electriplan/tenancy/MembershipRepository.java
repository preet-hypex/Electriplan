package com.hypex.electriplan.tenancy;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Memberships, through Hibernate; queries derived from the method names. */
interface MembershipRepository extends JpaRepository<MemberEntity, MemberId> {

    /**
     * A person's memberships that count: active, in a company that is not
     * closed. Row-level security (policy own_memberships) already limits the
     * rows to the caller's own.
     */
    @EntityGraph(attributePaths = "organisation")
    List<MemberEntity> findByIdUserIdAndStatusAndOrganisationStatusNotOrderByOrganisationNameAsc(
            UUID userId, MemberStatus status, LicenceStatus excluded);

    long countByIdOrganisationIdAndStatusAndRoleNot(UUID organisationId, MemberStatus status, MemberRole notRole);

    default List<MemberEntity> usableBy(UUID userId) {
        return findByIdUserIdAndStatusAndOrganisationStatusNotOrderByOrganisationNameAsc(
                userId, MemberStatus.ACTIVE, LicenceStatus.CLOSED);
    }
}
