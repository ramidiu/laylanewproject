package com.remitz.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends RemitzException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
