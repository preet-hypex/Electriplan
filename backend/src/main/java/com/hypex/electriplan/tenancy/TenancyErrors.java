package com.hypex.electriplan.tenancy;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Company access problems as {"message": "..."} with their status, the shape the web app reads. */
@RestControllerAdvice
class TenancyErrors {

    record ErrorBody(String message) {
    }

    @ExceptionHandler(CompanyAccessException.class)
    ResponseEntity<ErrorBody> companyAccess(CompanyAccessException e) {
        return ResponseEntity.status(e.status()).body(new ErrorBody(e.getMessage()));
    }
}
