package com.hypex.electriplan.projects;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hypex.electriplan.tenancy.CompanyContext;
import com.hypex.electriplan.tenancy.CurrentCompany;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What people do with projects: list, start, open, change, archive, restore.
 *
 * <p>Every query runs inside the request's company: row-level security hides
 * other companies' rows, so their ids are simply not found. The helpers each
 * step uses: {@link ProjectSearch} (the list's filters),
 * {@link ProjectDetailsCheck} (a form's checks), {@link EditVersion}
 * (refusing stale changes) and {@link ProjectViews} (what the API returns).
 */
@Service
@RequiredArgsConstructor
@Transactional
class ProjectsService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProjectRepository projects;
    private final HouseRepository houses;
    private final ProjectDetailsCheck detailsCheck;
    private final CurrentCompany current;

    /** One page of the company's projects, newest activity first, each with its houses counted by stage. */
    @Transactional(readOnly = true)
    ProjectsApi.ProjectPage list(@Nullable String words, @Nullable ProjectStatus status, boolean archived, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ProjectsProblem.invalid("size", "Pages start at 0 and hold 1 to " + MAX_PAGE_SIZE + " projects");
        }

        Page<ProjectEntity> found = projects.findAll(
                ProjectSearch.matching(words, status, archived),
                PageRequest.of(page, size, ProjectSearch.NEWEST_ACTIVITY_FIRST));
        Map<UUID, List<HouseRepository.StageCount>> housesByStage = housesByStage(found.getContent());

        List<ProjectsApi.ProjectSummary> rows = found.getContent().stream()
                .map(project -> ProjectViews.summary(project, housesByStage.getOrDefault(project.getId(), List.of())))
                .toList();
        return new ProjectsApi.ProjectPage(rows, page, size, found.getTotalElements());
    }

    /** For each project, how many of its (current) houses are at each stage: one query for the whole page. */
    private Map<UUID, List<HouseRepository.StageCount>> housesByStage(List<ProjectEntity> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = page.stream().map(ProjectEntity::getId).toList();
        return houses.countByStage(ids).stream().collect(Collectors.groupingBy(HouseRepository.StageCount::projectId));
    }

    /** A new project in the request's company. The database gives it the next reference (PRJ-000001...). */
    ProjectsApi.Project create(ProjectsApi.ProjectForm form) {
        CompanyContext company = current.require();
        ProjectEntity project = ProjectEntity.create(company.organisationId(), company.userId());
        project.changeDetails(detailsCheck.check(form, project.getSupplyPhases()));

        // Use what save returns: it carries what the database filled in (reference, timestamps).
        return ProjectViews.project(projects.saveAndFlush(project), List.of());
    }

    @Transactional(readOnly = true)
    ProjectsApi.Project get(UUID id) {
        return view(projects.require(id));
    }

    /** New details for a project, refused if it changed since the caller read it or is archived. */
    ProjectsApi.Project update(UUID id, ProjectsApi.ProjectForm form) {
        ProjectEntity project = projects.require(id);
        EditVersion.requireUnchanged(form.version(), project.getLockVersion());
        if (project.archived()) {
            throw ProjectsProblem.conflict("This project is archived. Restore it to change it.");
        }

        project.changeDetails(detailsCheck.check(form, project.getSupplyPhases()));
        return view(projects.saveAndFlush(project));
    }

    /** Out of the project list, and read-only with its houses, until restored. */
    ProjectsApi.Project archive(UUID id) {
        ProjectEntity project = projects.require(id);
        project.archive();
        return view(projects.saveAndFlush(project));
    }

    ProjectsApi.Project restore(UUID id) {
        ProjectEntity project = projects.require(id);
        project.restore();
        return view(projects.saveAndFlush(project));
    }

    private ProjectsApi.Project view(ProjectEntity project) {
        return ProjectViews.project(project, houses.findByProjectIdOrderByCreatedAtAscNameAsc(project.getId()));
    }
}
