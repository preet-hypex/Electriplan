package com.hypex.electriplan.projects.dao;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.projects.domain.HouseStage;
import com.hypex.electriplan.projects.domain.ProjectsProblem;
import com.hypex.electriplan.projects.entity.HouseEntity;
import com.hypex.electriplan.projects.entity.ProjectEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HouseRepository extends JpaRepository<HouseEntity, UUID> {

    /** The house, or 404: another company's house is not found, as if it did not exist. */
    default HouseEntity require(UUID id) {
        return findById(id).orElseThrow(() -> ProjectsProblem.notFound("house"));
    }

    /** A project's houses, oldest first. */
    List<HouseEntity> findByProjectIdOrderByCreatedAtAscNameAsc(UUID projectId);

    /** Houses that are not archived, by project and stage, for the project list. */
    @Query("""
            select new com.hypex.electriplan.projects.dao.HouseRepository$StageTally(h.projectId, h.stage, count(h))
              from HouseEntity h
             where h.projectId in :projects and h.archivedAt is null
             group by h.projectId, h.stage""")
    List<StageTally> countByStage(Collection<UUID> projects);

    public record StageTally(UUID projectId, HouseStage stage, long count) {
    }

    /** Houses that are not archived, in projects that are not archived, most recently changed first. */
    @Query("""
            select new com.hypex.electriplan.projects.dao.HouseRepository$HouseInProject(h, p)
              from HouseEntity h join ProjectEntity p on p.id = h.projectId
             where h.archivedAt is null and p.archivedAt is null
             order by h.updatedAt desc, h.name""")
    List<HouseInProject> findRecentlyChanged(Pageable page);

    /** A house with its project, read in one query. */
    public record HouseInProject(HouseEntity house, ProjectEntity project) {
    }
}
