package com.hypex.electriplan.reference.controller;

import com.hypex.electriplan.reference.domain.AddressSearchUnavailableException;
import com.hypex.electriplan.reference.dto.ErrorMessage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Reference endpoints' refusals as {"message": "..."}. */
@RestControllerAdvice(basePackageClasses = ReferenceErrors.class)
public class ReferenceErrors {

    @ExceptionHandler(AddressSearchUnavailableException.class)
    ResponseEntity<ErrorMessage> addressSearchUnavailable(AddressSearchUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorMessage(e.getMessage()));
    }
}
