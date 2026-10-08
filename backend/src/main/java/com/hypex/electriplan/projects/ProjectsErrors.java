package com.hypex.electriplan.projects;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** The projects endpoints' refusals as {"message"} or {"message", "errors": [{field, message}]}. */
@RestControllerAdvice(basePackageClasses = ProjectsErrors.class)
class ProjectsErrors {

    static final String CHECK_FIELDS = "Check the highlighted fields.";
    static final String CHANGED_SINCE = "Someone changed this since you opened it. Reload to see their changes, then make yours again.";

    @ExceptionHandler(ProjectsProblem.class)
    ResponseEntity<?> problem(ProjectsProblem e) {
        if (!e.fields().isEmpty()) {
            return ResponseEntity.status(e.status()).body(new ProjectsApi.ValidationProblem(e.getMessage(), e.fields()));
        }
        return ResponseEntity.status(e.status()).body(new ProjectsApi.ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProjectsApi.ValidationProblem> invalid(MethodArgumentNotValidException e) {
        List<ProjectsApi.FieldProblem> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ProjectsApi.FieldProblem(f.getField(), String.valueOf(f.getDefaultMessage())))
                .sorted(Comparator.comparing(ProjectsApi.FieldProblem::field).thenComparing(ProjectsApi.FieldProblem::message))
                .toList();
        return ResponseEntity.badRequest().body(new ProjectsApi.ValidationProblem(CHECK_FIELDS, fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProjectsApi.ErrorBody> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ProjectsApi.ErrorBody(
                "The request could not be read: it is not JSON, or a value is not one of the allowed ones."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProjectsApi.ErrorBody> badParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ProjectsApi.ErrorBody("'" + e.getName() + "' is not valid: " + e.getValue()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ProjectsApi.ErrorBody> changedSince(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ProjectsApi.ErrorBody(CHANGED_SINCE));
    }
}
