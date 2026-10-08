package com.hypex.electriplan.projects.dao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.FloorPlanState;
import com.hypex.electriplan.projects.entity.FloorPlanVersionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FloorPlanVersionRepository extends JpaRepository<FloorPlanVersionEntity, UUID> {

    /** The storey's draft, if it has one (at most one: the database enforces it). */
    default Optional<FloorPlanVersionEntity> findDraft(UUID levelId) {
        return findByLevelIdAndState(levelId, FloorPlanState.DRAFT);
    }

    Optional<FloorPlanVersionEntity> findByLevelIdAndState(UUID levelId, FloorPlanState state);

    /** The storey's newest committed version. */
    Optional<FloorPlanVersionEntity> findFirstByLevelIdAndStateOrderByVersionNoDesc(UUID levelId, FloorPlanState state);

    Optional<FloorPlanVersionEntity> findByLevelIdAndVersionNo(UUID levelId, int versionNo);

    /** Every version of the storey, newest first. */
    List<FloorPlanVersionEntity> findByLevelIdOrderByVersionNoDesc(UUID levelId);

    /** The number the storey's next version gets. */
    @Query("select coalesce(max(v.versionNo), 0) + 1 from FloorPlanVersionEntity v where v.levelId = :levelId")
    int nextVersionNo(UUID levelId);
}
