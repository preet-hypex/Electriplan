package com.hypex.electriplan.projects.service;

import java.util.Objects;
import java.util.UUID;

import com.hypex.electriplan.projects.dao.HouseRepository;
import com.hypex.electriplan.projects.dao.LevelRepository;
import com.hypex.electriplan.projects.dao.ProjectRepository;
import com.hypex.electriplan.projects.domain.DwellingType;
import com.hypex.electriplan.projects.domain.ProjectsProblem;
import com.hypex.electriplan.projects.dto.House;
import com.hypex.electriplan.projects.dto.HouseForm;
import com.hypex.electriplan.projects.entity.HouseEntity;
import com.hypex.electriplan.projects.entity.LevelEntity;
import com.hypex.electriplan.projects.entity.ProjectEntity;
import com.hypex.electriplan.tenancy.domain.CompanyContext;
import com.hypex.electriplan.tenancy.service.CurrentCompany;

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
public class HousesService {

    private final ProjectRepository projects;
    private final HouseRepository houses;
    private final LevelRepository levels;
    private final CurrentCompany current;

    /** A new house, awaiting its floor plan, with its ground floor ready for one. */
    public House add(UUID projectId, HouseForm form) {
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
    public House get(UUID id) {
        HouseEntity house = houses.require(id);
        return view(house, projects.require(house.getProjectId()));
    }

    /** A new name or dwelling type, refused if the house changed since the caller read it or is archived. */
    public House update(UUID id, HouseForm form) {
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

    public House archive(UUID id) {
        HouseEntity house = houses.require(id);
        house.archive();
        return view(houses.saveAndFlush(house), projects.require(house.getProjectId()));
    }

    /** Back in its project, unless the project itself is archived. */
    public House restore(UUID id) {
        HouseEntity house = houses.require(id);
        ProjectEntity project = projects.require(house.getProjectId());
        if (project.archived()) {
            throw ProjectsProblem.conflict("Its project is archived. Restore the project first.");
        }
        house.restore();
        return view(houses.saveAndFlush(house), project);
    }

    private House view(HouseEntity house, ProjectEntity project) {
        return ProjectViews.house(house, project, levels.findByHouseIdOrderByOrdinalAsc(house.getId()));
    }
}
