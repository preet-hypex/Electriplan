package com.hypex.electriplan.reference.dao;

import java.util.List;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.reference.entity.DistributorEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/** Distributors, through Hibernate. Queries are derived from the method names: no SQL. */
public interface DistributorRepository extends JpaRepository<DistributorEntity, String> {

    List<DistributorEntity> findAllByOrderByStateAscNameAsc();

    List<DistributorEntity> findByStateOrderByNameAsc(AustralianState state);
}
