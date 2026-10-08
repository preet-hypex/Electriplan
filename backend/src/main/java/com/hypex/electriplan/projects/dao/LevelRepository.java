package com.hypex.electriplan.projects.dao;

import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.projects.entity.LevelEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<LevelEntity, UUID> {

    List<LevelEntity> findByHouseIdOrderByOrdinalAsc(UUID houseId);
}
