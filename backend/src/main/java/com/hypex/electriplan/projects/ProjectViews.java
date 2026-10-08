package com.hypex.electriplan.projects;

import java.util.Comparator;
import java.util.List;

/** Turns database rows into what the API returns. Nothing here reads or writes the database. */
final class ProjectViews {

    private ProjectViews() {
    }

    /** A project with its houses. */
    static ProjectsApi.Project project(ProjectEntity p, List<HouseEntity> houses) {
        return new ProjectsApi.Project(
                p.getId(),
                p.getReference(),
                p.getName(),
                p.getDescription(),
                p.getStatus(),
                p.getLotNumber(),
                new ProjectsApi.Site(p.getSiteStreet(), p.getSiteSuburb(), p.getSiteState(), p.getSitePostcode()),
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
    static ProjectsApi.ProjectSummary summary(ProjectEntity p, List<HouseRepository.StageCount> housesByStage) {
        List<ProjectsApi.StageCount> stages = housesByStage.stream()
                .sorted(Comparator.comparing(HouseRepository.StageCount::stage))
                .map(c -> new ProjectsApi.StageCount(c.stage(), c.count()))
                .toList();
        long houseCount = stages.stream().mapToLong(ProjectsApi.StageCount::count).sum();
        return new ProjectsApi.ProjectSummary(
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

    static ProjectsApi.HouseSummary houseSummary(HouseEntity h) {
        return new ProjectsApi.HouseSummary(h.getId(), h.getName(), h.getDwellingType(), h.getStage(), h.archived(), h.getUpdatedAt());
    }

    /** A house with its project and storeys. */
    static ProjectsApi.House house(HouseEntity h, ProjectEntity project, List<LevelEntity> levels) {
        return new ProjectsApi.House(
                h.getId(),
                new ProjectsApi.ProjectRef(project.getId(), project.getReference(), project.getName()),
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

    static ProjectsApi.Level level(LevelEntity l) {
        return new ProjectsApi.Level(l.getId(), l.getName(), l.getOrdinal(), l.getCeilingHeightMm(), l.getCurrentFloorPlanVersionId());
    }
}
