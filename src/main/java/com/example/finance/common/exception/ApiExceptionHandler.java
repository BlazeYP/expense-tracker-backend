package com.example.finance.common.exception;

import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler({IllegalArgumentException.class, DuplicateKeyException.class})
    ResponseEntity<?> handleBadRequest(RuntimeException exception) {
        String message = exception.getMessage() == null ? "Invalid request" : exception.getMessage();
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413).body(Map.of("error", "Uploaded file exceeds 15 MB"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> handleUnexpected(Exception exception) {
        return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
    }
}
