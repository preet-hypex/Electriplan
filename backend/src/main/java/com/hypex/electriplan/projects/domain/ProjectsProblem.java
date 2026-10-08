package com.hypex.electriplan.projects.domain;

import java.util.List;

import com.hypex.electriplan.projects.controller.ProjectsErrors;
import com.hypex.electriplan.projects.dto.FieldProblem;

import org.springframework.http.HttpStatus;

/** A request the projects endpoints refuse, with what to tell the person. */
public class ProjectsProblem extends RuntimeException {

    private final HttpStatus status;
    private final List<FieldProblem> fields;

    private ProjectsProblem(HttpStatus status, String message, List<FieldProblem> fields) {
        super(message);
        this.status = status;
        this.fields = List.copyOf(fields);
    }

    public static ProjectsProblem notFound(String what) {
        return new ProjectsProblem(HttpStatus.NOT_FOUND, "No " + what + " with that id in this company.", List.of());
    }

    public static ProjectsProblem conflict(String message) {
        return new ProjectsProblem(HttpStatus.CONFLICT, message, List.of());
    }

    public static ProjectsProblem invalid(List<FieldProblem> fields) {
        return new ProjectsProblem(HttpStatus.BAD_REQUEST, ProjectsErrors.CHECK_FIELDS, fields);
    }

    public static ProjectsProblem invalid(String field, String message) {
        return invalid(List.of(new FieldProblem(field, message)));
    }

    public HttpStatus status() {
        return status;
    }

    public List<FieldProblem> fields() {
        return fields;
    }
}
