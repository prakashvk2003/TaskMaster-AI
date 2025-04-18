package com.prakash.taskmaster.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT) // 409 Conflict is appropriate for this validation failure
public class CircularDependencyException extends RuntimeException {

    public CircularDependencyException(String message) {
        super(message);
    }
}