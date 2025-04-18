package com.prakash.taskmaster.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT) // 409 Conflict is suitable for invalid state transitions
public class InvalidTaskStateException extends RuntimeException {

    public InvalidTaskStateException(String message) {
        super(message);
    }
}