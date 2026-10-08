package com.hypex.electriplan.projects;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.projects.ProjectsApi.FieldProblem;
import com.hypex.electriplan.reference.Distributor;
import com.hypex.electriplan.reference.DistributorDirectory;
import com.hypex.electriplan.tenancy.CompanyContext;
import com.hypex.electriplan.tenancy.CurrentCompany;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects and houses of the request's company. Row-level security limits
 * every query to that company, so another company's id is simply not found.
 */
@Service
@RequiredArgsConstructor
@Transactional
class ProjectsService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProjectRepository projects;
    private final HouseRepository houses;
    private final LevelRepository levels;
    private final DistributorDirectory distributors;
    private final CurrentCompany current;

    // ---- Projects ----

    @Transactional(readOnly = true)
    ProjectsApi.ProjectPage list(@Nullable String query, @Nullable ProjectStatus status, boolean archived, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ProjectsProblem.invalid("size", "Pages start at 0 and hold 1 to " + MAX_PAGE_SIZE + " projects");
        }
        Page<ProjectEntity> found = projects.findAll(matching(query, status, archived),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("lastActivityAt"), Sort.Order.desc("reference"))));

        List<UUID> ids = found.getContent().stream().map(ProjectEntity::getId).toList();
        Map<UUID, List<HouseRepository.StageCount>> counts = ids.isEmpty() ? Map.of()
                : houses.countByStage(ids).stream().collect(Collectors.groupingBy(HouseRepository.StageCount::projectId));

        List<ProjectsApi.ProjectSummary> items = found.getContent().stream().map(p -> {
            List<ProjectsApi.StageCount> stages = counts.getOrDefault(p.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(HouseRepository.StageCount::stage))
                    .map(c -> new ProjectsApi.StageCount(c.stage(), c.count()))
                    .toList();
            long houseCount = stages.stream().mapToLong(ProjectsApi.StageCount::count).sum();
            return new ProjectsApi.ProjectSummary(p.getId(), p.getReference(), p.getName(), p.getSiteSuburb(),
                    p.getSiteState(), p.getStatus(), p.archived(), houseCount, stages, p.getLastActivityAt());
        }).toList();
        return new ProjectsApi.ProjectPage(items, page, size, found.getTotalElements());
    }

    private static Specification<ProjectEntity> matching(@Nullable String query, @Nullable ProjectStatus status, boolean archived) {
        return (root, q, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String like = "%" + query.strip().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like, '\\'),
                        cb.like(cb.lower(root.get("reference")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("siteSuburb"), "")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("siteStreet"), "")), like, '\\')));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    ProjectsApi.Project create(ProjectsApi.ProjectForm form) {
        CompanyContext company = current.require();
        ProjectEntity project = ProjectEntity.create(company.organisationId(), company.userId());
        apply(project, form);
        return project(projects.saveAndFlush(project));
    }

    @Transactional(readOnly = true)
    ProjectsApi.Project get(UUID id) {
        return project(find(id));
    }

    ProjectsApi.Project update(UUID id, ProjectsApi.ProjectForm form) {
        ProjectEntity project = find(id);
        requireCurrent(form.version(), project.getLockVersion());
        if (project.archived()) {
            throw ProjectsProblem.conflict("This project is archived. Restore it to change it.");
        }
        apply(project, form);
        return project(projects.saveAndFlush(project));
    }

    ProjectsApi.Project archive(UUID id, boolean archive) {
        ProjectEntity project = find(id);
        if (project.archived() != archive) {
            project.setArchivedAt(archive ? Instant.now() : null);
            projects.saveAndFlush(project);
        }
        return project(project);
    }

    private void apply(ProjectEntity project, ProjectsApi.ProjectForm form) {
        ProjectsApi.SiteForm site = Objects.requireNonNull(form.site());
        AustralianState state = Objects.requireNonNull(site.state());
        List<FieldProblem> problems = new ArrayList<>();
        String distributor = checkDistributor(form.distributor(), state, problems);
        int phases = form.supplyPhases() == null ? project.getSupplyPhases() : form.supplyPhases();
        if (phases != 1 && phases != 3) {
            problems.add(new FieldProblem("supplyPhases", "Supply is single-phase (1) or three-phase (3)"));
        }
        if (!problems.isEmpty()) {
            throw ProjectsProblem.invalid(problems);
        }
        project.setName(Objects.requireNonNull(form.name()).strip());
        project.setDescription(blankToNull(form.description()));
        project.setLotNumber(blankToNull(form.lotNumber()));
        project.setSiteStreet(blankToNull(site.street()));
        project.setSiteSuburb(blankToNull(site.suburb()));
        project.setSiteState(state);
        project.setSitePostcode(blankToNull(site.postcode()));
        project.setDistributorCode(distributor);
        project.setSupplyPhases((short) phases);
        project.setDueOn(form.dueOn());
        if (form.status() != null) {
            project.setStatus(form.status());
        }
    }

    /** The distributor's code if it is one we know, in the site's state; problems recorded otherwise. */
    private @Nullable String checkDistributor(@Nullable String code, AustralianState state, List<FieldProblem> problems) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Optional<Distributor> found;
        try {
            found = distributors.find(DistributorCode.of(code.strip()));
        } catch (IllegalArgumentException e) {
            found = Optional.empty();
        }
        String choices = distributors.inState(state).stream().map(d -> d.code().value()).collect(Collectors.joining(", "));
        String choose = choices.isEmpty() ? "No distributor in " + state + " is set up yet: leave it empty."
                : "Choose one of " + choices + ".";
        if (found.isEmpty()) {
            problems.add(new FieldProblem("distributor", "No distributor has the code '" + code + "'. " + choose));
            return null;
        }
        if (found.get().state() != state) {
            problems.add(new FieldProblem("distributor",
                    found.get().name() + " supplies " + found.get().state() + ", not " + state + ". " + choose));
            return null;
        }
        return found.get().code().value();
    }

    private ProjectEntity find(UUID id) {
        return projects.findById(id).orElseThrow(() -> ProjectsProblem.notFound("project"));
    }

    private ProjectsApi.Project project(ProjectEntity p) {
        List<ProjectsApi.HouseSummary> its = houses.findByProjectIdOrderByCreatedAtAscNameAsc(p.getId()).stream()
                .map(h -> new ProjectsApi.HouseSummary(h.getId(), h.getName(), h.getDwellingType(), h.getStage(),
                        h.archived(), h.getUpdatedAt()))
                .toList();
        return new ProjectsApi.Project(p.getId(), p.getReference(), p.getName(), p.getDescription(), p.getStatus(),
                p.getLotNumber(), new ProjectsApi.Site(p.getSiteStreet(), p.getSiteSuburb(), p.getSiteState(), p.getSitePostcode()),
                p.getDistributorCode(), p.getSupplyPhases(), p.getDueOn(), p.archived(), p.getArchivedAt(),
                p.getCreatedAt(), p.getLastActivityAt(), p.getLockVersion(), its);
    }

    // ---- Houses ----

    ProjectsApi.House addHouse(UUID projectId, ProjectsApi.HouseForm form) {
        ProjectEntity project = find(projectId);
        if (project.archived()) {
            throw ProjectsProblem.conflict("This project is archived. Restore it to add a house.");
        }
        CompanyContext company = current.require();
        HouseEntity house = HouseEntity.create(company.organisationId(), project.getId(), company.userId());
        house.setName(Objects.requireNonNull(form.name()).strip());
        house.setDwellingType(form.dwellingType() == null ? DwellingType.HOUSE : form.dwellingType());
        houses.saveAndFlush(house);
        levels.saveAndFlush(LevelEntity.groundFloor(house));
        return house(house, project);
    }

    @Transactional(readOnly = true)
    ProjectsApi.House getHouse(UUID id) {
        HouseEntity house = findHouse(id);
        return house(house, find(house.getProjectId()));
    }

    ProjectsApi.House updateHouse(UUID id, ProjectsApi.HouseForm form) {
        HouseEntity house = findHouse(id);
        requireCurrent(form.version(), house.getLockVersion());
        ProjectEntity project = find(house.getProjectId());
        if (house.archived() || project.archived()) {
            throw ProjectsProblem.conflict("This house is archived, or its project is. Restore it to change it.");
        }
        house.setName(Objects.requireNonNull(form.name()).strip());
        if (form.dwellingType() != null) {
            house.setDwellingType(form.dwellingType());
        }
        return house(houses.saveAndFlush(house), project);
    }

    ProjectsApi.House archiveHouse(UUID id, boolean archive) {
        HouseEntity house = findHouse(id);
        ProjectEntity project = find(house.getProjectId());
        if (!archive && project.archived()) {
            throw ProjectsProblem.conflict("Its project is archived. Restore the project first.");
        }
        if (house.archived() != archive) {
            house.setArchivedAt(archive ? Instant.now() : null);
            houses.saveAndFlush(house);
        }
        return house(house, project);
    }

    private HouseEntity findHouse(UUID id) {
        return houses.findById(id).orElseThrow(() -> ProjectsProblem.notFound("house"));
    }

    private ProjectsApi.House house(HouseEntity h, ProjectEntity p) {
        List<ProjectsApi.Level> its = levels.findByHouseIdOrderByOrdinalAsc(h.getId()).stream()
                .map(l -> new ProjectsApi.Level(l.getId(), l.getName(), l.getOrdinal(), l.getCeilingHeightMm(),
                        l.getCurrentFloorPlanVersionId()))
                .toList();
        return new ProjectsApi.House(h.getId(), new ProjectsApi.ProjectRef(p.getId(), p.getReference(), p.getName()),
                h.getName(), h.getDwellingType(), h.getStoreys(), h.getStage(), h.getStageChangedAt(), its, h.archived(),
                h.getArchivedAt(), h.getCreatedAt(), h.getUpdatedAt(), h.getLockVersion());
    }

    // ---- Shared ----

    /** A change must name the version it was made from: a newer one means someone else changed it since. */
    private static void requireCurrent(@Nullable Integer sent, int now) {
        if (sent == null) {
            throw ProjectsProblem.invalid("version", "Send the version you are changing (from when you opened it)");
        }
        if (sent != now) {
            throw ProjectsProblem.conflict(ProjectsErrors.CHANGED_SINCE);
        }
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
