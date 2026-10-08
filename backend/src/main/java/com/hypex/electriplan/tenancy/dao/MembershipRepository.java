package com.hypex.electriplan.tenancy.dao;

import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.tenancy.domain.LicenceStatus;
import com.hypex.electriplan.tenancy.domain.MemberRole;
import com.hypex.electriplan.tenancy.domain.MemberStatus;
import com.hypex.electriplan.tenancy.entity.MemberEntity;
import com.hypex.electriplan.tenancy.entity.MemberId;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Memberships, through Hibernate; queries derived from the method names. */
public interface MembershipRepository extends JpaRepository<MemberEntity, MemberId> {

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
