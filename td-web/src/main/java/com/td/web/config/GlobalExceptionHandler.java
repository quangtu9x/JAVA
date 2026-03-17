package com.td.web.config;

import com.td.application.common.models.Result;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::formatFieldError)
            .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "Du lieu khong hop le";
        }

        return ResponseEntity.badRequest().body(Result.failure(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations()
            .stream()
            .map(violation -> violation.getMessage())
            .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "Du lieu khong hop le";
        }

        return ResponseEntity.badRequest().body(Result.failure(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Result.failure("Noi dung yeu cau khong hop le"));
    }

    private String formatFieldError(FieldError error) {
        return error.getDefaultMessage() == null
            ? "Du lieu khong hop le"
            : error.getDefaultMessage();
    }
}
