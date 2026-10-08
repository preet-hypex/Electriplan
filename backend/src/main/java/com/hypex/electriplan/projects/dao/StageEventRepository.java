package com.hypex.electriplan.projects.dao;

import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.projects.entity.StageEventEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StageEventRepository extends JpaRepository<StageEventEntity, Long> {

    /** A house's stage history, newest first. */
    List<StageEventEntity> findByHouseIdOrderByOccurredAtDescIdDesc(UUID houseId);
}
