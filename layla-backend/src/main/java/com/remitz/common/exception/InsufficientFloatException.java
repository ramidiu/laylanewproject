package com.remitz.common.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFloatException extends RemitzException {

    public InsufficientFloatException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
