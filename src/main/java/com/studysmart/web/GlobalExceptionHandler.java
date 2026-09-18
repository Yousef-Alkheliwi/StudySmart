package com.studysmart.web;

import com.studysmart.exception.IngestionException;
import com.studysmart.exception.NotFoundException;
import com.studysmart.exception.ValidationException;
import com.studysmart.web.dto.ErrorBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler({ValidationException.class, MethodArgumentNotValidException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorBody> handleBadRequest(Exception e) {
        String message = e instanceof MethodArgumentNotValidException manv
                ? manv.getBindingResult().getFieldErrors().stream()
                        .map(f -> f.getField() + " " + f.getDefaultMessage())
                        .findFirst().orElse("Invalid request")
                : e.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(message));
    }

    @ExceptionHandler(IngestionException.class)
    public ResponseEntity<ErrorBody> handleIngestion(IngestionException e) {
        log.warn("Ingestion error", e);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ErrorBody(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneric(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorBody("Internal server error"));
    }
}
