package com.hypex.electriplan.projects;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface HouseRepository extends JpaRepository<HouseEntity, UUID> {

    /** The house, or 404: another company's house is not found, as if it did not exist. */
    default HouseEntity require(UUID id) {
        return findById(id).orElseThrow(() -> ProjectsProblem.notFound("house"));
    }

    /** A project's houses, oldest first. */
    List<HouseEntity> findByProjectIdOrderByCreatedAtAscNameAsc(UUID projectId);

    /** Houses that are not archived, by project and stage, for the project list. */
    @Query("""
            select new com.hypex.electriplan.projects.HouseRepository$StageCount(h.projectId, h.stage, count(h))
              from HouseEntity h
             where h.projectId in :projects and h.archivedAt is null
             group by h.projectId, h.stage""")
    List<StageCount> countByStage(Collection<UUID> projects);

    record StageCount(UUID projectId, HouseStage stage, long count) {
    }
}
