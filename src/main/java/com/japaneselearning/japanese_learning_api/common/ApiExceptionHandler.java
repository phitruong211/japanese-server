package com.japaneselearning.japanese_learning_api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.status()).body(error(ex.status(), ex.getMessage(), request.getRequestURI(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", request.getRequestURI(), fields));
    }

    private ErrorResponse error(HttpStatus status, String message, String path, Map<String, String> fields) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fields);
    }

    public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
                                Map<String, String> fieldErrors) {}
}
