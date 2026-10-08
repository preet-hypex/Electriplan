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

    /** 404 with its own words, for something that does not exist yet rather than an unknown id. */
    public static ProjectsProblem missing(String message) {
        return new ProjectsProblem(HttpStatus.NOT_FOUND, message, List.of());
    }

    /** 422: the request was understood but could not be done, e.g. the analyser could not read the plan. */
    public static ProjectsProblem unprocessable(String message) {
        return new ProjectsProblem(HttpStatus.UNPROCESSABLE_ENTITY, message, List.of());
    }

    /** 503: a service this needs is not answering. */
    public static ProjectsProblem unavailable(String message) {
        return new ProjectsProblem(HttpStatus.SERVICE_UNAVAILABLE, message, List.of());
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
