package com.hypex.electriplan.reference;

import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.common.AustralianState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * A row of {@code electriplan.electricity_distributor}. Read-only: the rows are
 * reference data seeded by migrations, never written by the application.
 */
@Entity
@Immutable
@Table(schema = "electriplan", name = "electricity_distributor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class DistributorEntity {

    @Id
    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private AustralianState state;

    Distributor toDistributor() {
        return new Distributor(DistributorCode.of(code), name, state);
    }
}
