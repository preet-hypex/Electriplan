package com.hypex.electriplan.projects.dao;

import com.hypex.electriplan.projects.entity.StageTransitionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StageTransitionRepository extends JpaRepository<StageTransitionEntity, StageTransitionEntity.Move> {
}
