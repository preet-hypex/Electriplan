package com.hypex.electriplan.tenancy.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.hypex.electriplan.tenancy.domain.LicenceStatus;
import com.hypex.electriplan.tenancy.dto.Licence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** A company (electriplan.organisation): the columns tenancy needs. Others are mapped as later stories need them. */
@Entity
@Table(schema = "electriplan", name = "organisation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OrganisationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false)
    private String slug;

    @Convert(converter = CodeConverters.Licence.class)
    @Column(name = "status", nullable = false)
    private LicenceStatus status;

    @Column(name = "seat_limit", nullable = false)
    private int seatLimit;

    @Column(name = "licence_starts_on", nullable = false)
    private LocalDate licenceStartsOn;

    @Column(name = "licence_ends_on")
    private @Nullable LocalDate licenceEndsOn;

    @Column(name = "closed_at")
    private @Nullable Instant closedAt;
}
