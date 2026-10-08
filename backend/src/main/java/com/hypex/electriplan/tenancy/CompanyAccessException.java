package com.hypex.electriplan.tenancy;

import org.springframework.http.HttpStatus;

/** The request cannot act in the company it asked for (or did not say which). Carries the HTTP status and a message a person can act on. */
class CompanyAccessException extends RuntimeException {

    private final HttpStatus status;

    CompanyAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    HttpStatus status() {
        return status;
    }
}
