package com.hypex.electriplan.projects.dao;

import java.util.UUID;

import com.hypex.electriplan.projects.entity.AnalysisRunEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRunEntity, UUID> {
}
