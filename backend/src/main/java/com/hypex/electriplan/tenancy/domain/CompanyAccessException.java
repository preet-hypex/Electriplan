package com.hypex.electriplan.tenancy.domain;

import org.springframework.http.HttpStatus;

/** The request cannot act in the company it asked for (or did not say which). Carries the HTTP status and a message a person can act on. */
public class CompanyAccessException extends RuntimeException {

    private final HttpStatus status;

    public CompanyAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
