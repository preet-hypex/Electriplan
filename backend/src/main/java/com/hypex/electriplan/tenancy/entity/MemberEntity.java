package com.hypex.electriplan.tenancy.entity;

import java.time.Instant;

import com.hypex.electriplan.tenancy.domain.MemberRole;
import com.hypex.electriplan.tenancy.domain.MemberStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A person's membership of a company (electriplan.organisation_member). */
@Entity
@Table(schema = "electriplan", name = "organisation_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MemberEntity {

    @EmbeddedId
    private MemberId id;

    @MapsId("organisationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organisation_id")
    private OrganisationEntity organisation;

    @Convert(converter = CodeConverters.Role.class)
    @Column(name = "role", nullable = false)
    private MemberRole role;

    @Convert(converter = CodeConverters.Status.class)
    @Column(name = "status", nullable = false)
    private MemberStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;
}
