package com.bank2budget.exception;

public class CamtParseException extends RuntimeException {

    public CamtParseException(String message) {
        super(message);
    }

    public CamtParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
