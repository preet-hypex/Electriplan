package com.hypex.electriplan.projects.service;

import java.util.Comparator;
import java.util.List;

import com.hypex.electriplan.projects.dao.HouseRepository;
import com.hypex.electriplan.projects.dto.House;
import com.hypex.electriplan.projects.dto.HouseSummary;
import com.hypex.electriplan.projects.dto.Level;
import com.hypex.electriplan.projects.dto.Project;
import com.hypex.electriplan.projects.dto.ProjectRef;
import com.hypex.electriplan.projects.dto.ProjectSummary;
import com.hypex.electriplan.projects.dto.Site;
import com.hypex.electriplan.projects.dto.StageCount;
import com.hypex.electriplan.projects.entity.HouseEntity;
import com.hypex.electriplan.projects.entity.LevelEntity;
import com.hypex.electriplan.projects.entity.ProjectEntity;

/** Turns database rows into what the API returns. Nothing here reads or writes the database. */
public final class ProjectViews {

    private ProjectViews() {
    }

    /** A project with its houses. */
    static Project project(ProjectEntity p, List<HouseEntity> houses) {
        return new Project(
                p.getId(),
                p.getReference(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                p.getLotNumber(),
                new Site(p.getSiteStreet(), p.getSiteSuburb(), p.getSiteState(), p.getSitePostcode()),
                p.getDistributorCode(),
                p.getSupplyPhases(),
                p.getDueOn(),
                p.archived(),
                p.getArchivedAt(),
                p.getCreatedAt(),
                p.getLastActivityAt(),
                p.getLockVersion(),
                houses.stream().map(ProjectViews::houseSummary).toList());
    }

    /** A row of the project list: the project, and how many of its houses are at each stage. */
    static ProjectSummary summary(ProjectEntity p, List<HouseRepository.StageTally> housesByStage) {
        List<StageCount> stages = housesByStage.stream()
                .sorted(Comparator.comparing(HouseRepository.StageTally::stage))
                .map(c -> new StageCount(c.stage(), c.count()))
                .toList();
        long houseCount = stages.stream().mapToLong(StageCount::count).sum();
        return new ProjectSummary(
                p.getId(),
                p.getReference(),
                p.getName(),
                p.getSiteSuburb(),
                p.getSiteState(),
                p.getStatus(),
                p.archived(),
                houseCount,
                stages,
                p.getLastActivityAt());
    }

    static HouseSummary houseSummary(HouseEntity h) {
        return new HouseSummary(h.getId(), h.getName(), h.getDwellingType(), h.getStage(), h.archived(), h.getUpdatedAt());
    }

    /** A house with its project and storeys. */
    static House house(HouseEntity h, ProjectEntity project, List<LevelEntity> levels) {
        return new House(
                h.getId(),
                new ProjectRef(project.getId(), project.getReference(), project.getName()),
                h.getName(),
                h.getDwellingType(),
                h.getStoreys(),
                h.getStage(),
                h.getStageChangedAt(),
                levels.stream().map(ProjectViews::level).toList(),
                h.archived(),
                h.getArchivedAt(),
                h.getCreatedAt(),
                h.getUpdatedAt(),
                h.getLockVersion());
    }

    static Level level(LevelEntity l) {
        return new Level(l.getId(), l.getName(), l.getOrdinal(), l.getCeilingHeightMm(), l.getCurrentFloorPlanVersionId());
    }
}
