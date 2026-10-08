package com.hypex.electriplan.projects;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LevelRepository extends JpaRepository<LevelEntity, UUID> {

    List<LevelEntity> findByHouseIdOrderByOrdinalAsc(UUID houseId);
}
