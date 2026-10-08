package com.hypex.electriplan.tenancy.controller;

import com.hypex.electriplan.tenancy.domain.CompanyAccessException;
import com.hypex.electriplan.tenancy.dto.ErrorMessage;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Company access problems as {"message": "..."} with their status, the shape the web app reads. */
@RestControllerAdvice
public class TenancyErrors {

    @ExceptionHandler(CompanyAccessException.class)
    ResponseEntity<ErrorMessage> companyAccess(CompanyAccessException e) {
        return ResponseEntity.status(e.status()).body(new ErrorMessage(e.getMessage()));
    }
}
