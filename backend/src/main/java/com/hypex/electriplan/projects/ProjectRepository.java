package com.hypex.electriplan.projects;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Projects of the request's company: row-level security hides every other company's. */
interface ProjectRepository extends JpaRepository<ProjectEntity, UUID>, JpaSpecificationExecutor<ProjectEntity> {

    /** The project, or 404: another company's project is not found, as if it did not exist. */
    default ProjectEntity require(UUID id) {
        return findById(id).orElseThrow(() -> ProjectsProblem.notFound("project"));
    }
}
