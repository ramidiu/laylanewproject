package com.remitz.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RemitzException extends RuntimeException {

    private final HttpStatus httpStatus;

    public RemitzException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public RemitzException(String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}
