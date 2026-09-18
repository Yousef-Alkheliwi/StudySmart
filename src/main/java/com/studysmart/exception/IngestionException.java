package com.studysmart.exception;

public class IngestionException extends RuntimeException {
    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
