package com.hypex.electriplan.projects.dto;

import java.util.List;

/** A 400 that names the fields to fix. */
public record ValidationProblem(String message, List<FieldProblem> errors) {
}
