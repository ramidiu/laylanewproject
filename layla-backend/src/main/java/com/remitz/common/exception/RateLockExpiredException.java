package com.remitz.common.exception;

import org.springframework.http.HttpStatus;

public class RateLockExpiredException extends RemitzException {

    public RateLockExpiredException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public RateLockExpiredException(String quoteId, String expiryTime) {
        super(String.format("Rate lock for quote '%s' expired at '%s'", quoteId, expiryTime), HttpStatus.CONFLICT);
    }
}
