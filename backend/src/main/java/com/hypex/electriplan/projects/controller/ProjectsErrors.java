package com.hypex.electriplan.projects.controller;

import java.util.Comparator;
import java.util.List;

import com.hypex.electriplan.projects.domain.ProjectsProblem;
import com.hypex.electriplan.projects.dto.ErrorMessage;
import com.hypex.electriplan.projects.dto.FieldProblem;
import com.hypex.electriplan.projects.dto.ValidationProblem;

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
public class ProjectsErrors {

    public static final String CHECK_FIELDS = "Check the highlighted fields.";
    public static final String CHANGED_SINCE = "Someone changed this since you opened it. Reload to see their changes, then make yours again.";

    @ExceptionHandler(ProjectsProblem.class)
    ResponseEntity<?> problem(ProjectsProblem e) {
        if (!e.fields().isEmpty()) {
            return ResponseEntity.status(e.status()).body(new ValidationProblem(e.getMessage(), e.fields()));
        }
        return ResponseEntity.status(e.status()).body(new ErrorMessage(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ValidationProblem> invalid(MethodArgumentNotValidException e) {
        List<FieldProblem> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), String.valueOf(f.getDefaultMessage())))
                .sorted(Comparator.comparing(FieldProblem::field).thenComparing(FieldProblem::message))
                .toList();
        return ResponseEntity.badRequest().body(new ValidationProblem(CHECK_FIELDS, fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorMessage> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorMessage(
                "The request could not be read: it is not JSON, or a value is not one of the allowed ones."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorMessage> badParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorMessage("'" + e.getName() + "' is not valid: " + e.getValue()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorMessage> changedSince(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorMessage(CHANGED_SINCE));
    }
}
