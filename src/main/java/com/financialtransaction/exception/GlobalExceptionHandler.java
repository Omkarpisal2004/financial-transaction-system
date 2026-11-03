package com.financialtransaction.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.AuthenticationException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return errorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex) {

        if (ex.getMessage() != null
                && ex.getMessage().startsWith("Email already registered:")) {
            return errorResponse(HttpStatus.CONFLICT, ex.getMessage());
        }

        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {

        return errorResponse(
                HttpStatus.CONFLICT,
                "The request conflicts with existing data."
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(
            HttpMessageNotReadableException ex) {

        return errorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request body."
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex) {

        return errorResponse(
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action."
        );
    }

    private ResponseEntity<Map<String, Object>> errorResponse(
            HttpStatus status, String message) {

        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message == null ? "Request failed." : message
        ));
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex) {

        return errorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password."
        );
    }
}
