package com.hypex.electriplan.projects;

import java.util.Objects;
import java.util.UUID;

import com.hypex.electriplan.tenancy.CompanyContext;
import com.hypex.electriplan.tenancy.CurrentCompany;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What people do with a project's houses: add, open, change, archive,
 * restore. A house of an archived project is read-only too.
 */
@Service
@RequiredArgsConstructor
@Transactional
class HousesService {

    private final ProjectRepository projects;
    private final HouseRepository houses;
    private final LevelRepository levels;
    private final CurrentCompany current;

    /** A new house, awaiting its floor plan, with its ground floor ready for one. */
    ProjectsApi.House add(UUID projectId, ProjectsApi.HouseForm form) {
        ProjectEntity project = projects.require(projectId);
        if (project.archived()) {
            throw ProjectsProblem.conflict("This project is archived. Restore it to add a house.");
        }

        CompanyContext company = current.require();
        HouseEntity house = HouseEntity.create(company.organisationId(), project.getId(),
                Objects.requireNonNull(form.name(), "validated: name").strip(),
                form.dwellingType() == null ? DwellingType.HOUSE : form.dwellingType(),
                company.userId());
        // Use what save returns: it carries what the database filled in (timestamps, stage time).
        HouseEntity saved = houses.saveAndFlush(house);
        levels.saveAndFlush(LevelEntity.groundFloor(saved));
        return view(saved, project);
    }

    @Transactional(readOnly = true)
    ProjectsApi.House get(UUID id) {
        HouseEntity house = houses.require(id);
        return view(house, projects.require(house.getProjectId()));
    }

    /** A new name or dwelling type, refused if the house changed since the caller read it or is archived. */
    ProjectsApi.House update(UUID id, ProjectsApi.HouseForm form) {
        HouseEntity house = houses.require(id);
        EditVersion.requireUnchanged(form.version(), house.getLockVersion());
        ProjectEntity project = projects.require(house.getProjectId());
        if (house.archived() || project.archived()) {
            throw ProjectsProblem.conflict("This house is archived, or its project is. Restore it to change it.");
        }

        house.rename(Objects.requireNonNull(form.name(), "validated: name").strip());
        if (form.dwellingType() != null) {
            house.changeDwellingType(form.dwellingType());
        }
        return view(houses.saveAndFlush(house), project);
    }

    ProjectsApi.House archive(UUID id) {
        HouseEntity house = houses.require(id);
        house.archive();
        return view(houses.saveAndFlush(house), projects.require(house.getProjectId()));
    }

    /** Back in its project, unless the project itself is archived. */
    ProjectsApi.House restore(UUID id) {
        HouseEntity house = houses.require(id);
        ProjectEntity project = projects.require(house.getProjectId());
        if (project.archived()) {
            throw ProjectsProblem.conflict("Its project is archived. Restore the project first.");
        }
        house.restore();
        return view(houses.saveAndFlush(house), project);
    }

    private ProjectsApi.House view(HouseEntity house, ProjectEntity project) {
        return ProjectViews.house(house, project, levels.findByHouseIdOrderByOrdinalAsc(house.getId()));
    }
}
