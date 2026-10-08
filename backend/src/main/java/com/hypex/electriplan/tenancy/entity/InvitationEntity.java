package com.hypex.electriplan.tenancy.entity;

import java.time.Instant;
import java.util.UUID;

import com.hypex.electriplan.tenancy.domain.MemberRole;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** An invitation to join a company (electriplan.organisation_invitation): read here for seat counting; written by story T7. */
@Entity
@Table(schema = "electriplan", name = "organisation_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "organisation_id", nullable = false)
    private UUID organisationId;

    @Column(name = "email", nullable = false)
    private String email;

    @Convert(converter = CodeConverters.Role.class)
    @Column(name = "role", nullable = false)
    private MemberRole role;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private @Nullable Instant acceptedAt;

    @Column(name = "revoked_at")
    private @Nullable Instant revokedAt;
}
