/**
 * The projects workspace: a company's projects (a job at one site) and their
 * houses, each house with its storeys (documents/projects-workspace-plan.md,
 * Epic P). Everything is limited to the request's company by row-level
 * security and checked against the caller's permissions.
 *
 * <p>"House" here is the database's {@code plan}; "level" its {@code plan_level}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Projects", allowedDependencies = {"model", "tenancy :: domain", "tenancy :: service", "reference :: service", "reference :: dto",
        "files :: service", "files :: dto", "files :: domain"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.projects;
