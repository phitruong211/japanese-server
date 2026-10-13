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
        return ResponseEntity.status(ex.status()).body(new ErrorResponse(Instant.now(),ex.status().value(),ex.status().getReasonPhrase(),ex.getMessage(),request.getRequestURI(),null,ex.code()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ", request.getRequestURI(), fields));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> concurrentUpdate(org.springframework.orm.ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT,
                "Dữ liệu đã thay đổi. Hãy tải lại và thử lại.", request.getRequestURI(), null));
    }

    private ErrorResponse error(HttpStatus status, String message, String path, Map<String, String> fields) {
        return new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, path, fields, null);
    }

    public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
                                Map<String, String> fieldErrors, String code) {}
}
