package com.hypex.electriplan.projects;

import java.util.List;

import org.springframework.http.HttpStatus;

/** A request the projects endpoints refuse, with what to tell the person. */
class ProjectsProblem extends RuntimeException {

    private final HttpStatus status;
    private final List<ProjectsApi.FieldProblem> fields;

    private ProjectsProblem(HttpStatus status, String message, List<ProjectsApi.FieldProblem> fields) {
        super(message);
        this.status = status;
        this.fields = List.copyOf(fields);
    }

    static ProjectsProblem notFound(String what) {
        return new ProjectsProblem(HttpStatus.NOT_FOUND, "No " + what + " with that id in this company.", List.of());
    }

    static ProjectsProblem conflict(String message) {
        return new ProjectsProblem(HttpStatus.CONFLICT, message, List.of());
    }

    static ProjectsProblem invalid(List<ProjectsApi.FieldProblem> fields) {
        return new ProjectsProblem(HttpStatus.BAD_REQUEST, ProjectsErrors.CHECK_FIELDS, fields);
    }

    static ProjectsProblem invalid(String field, String message) {
        return invalid(List.of(new ProjectsApi.FieldProblem(field, message)));
    }

    HttpStatus status() {
        return status;
    }

    List<ProjectsApi.FieldProblem> fields() {
        return fields;
    }
}
