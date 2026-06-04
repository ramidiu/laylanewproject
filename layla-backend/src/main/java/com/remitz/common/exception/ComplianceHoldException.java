package com.remitz.common.exception;

import org.springframework.http.HttpStatus;

public class ComplianceHoldException extends RemitzException {

    public ComplianceHoldException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
