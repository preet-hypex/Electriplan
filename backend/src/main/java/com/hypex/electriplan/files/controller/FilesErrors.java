package com.hypex.electriplan.files.controller;

import com.hypex.electriplan.files.domain.FileNotFoundException;
import com.hypex.electriplan.files.domain.FileStorageException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The file store's failures, as {"message": "..."}. */
@RestControllerAdvice(basePackageClasses = FilesErrors.class)
public class FilesErrors {

    public record Message(String message) {
    }

    @ExceptionHandler(FileNotFoundException.class)
    ResponseEntity<Message> notFound(FileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new Message(e.getMessage()));
    }

    @ExceptionHandler(FileStorageException.class)
    ResponseEntity<Message> storage(FileStorageException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Message("File storage is not available right now. Try again in a moment."));
    }
}
