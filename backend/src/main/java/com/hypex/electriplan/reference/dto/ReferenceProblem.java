package com.hypex.electriplan.reference.dto;

/**
 * Something in a document that does not match the reference data: which field,
 * and what is wrong, in words a person can act on.
 */
public record ReferenceProblem(String field, String message) {
}
