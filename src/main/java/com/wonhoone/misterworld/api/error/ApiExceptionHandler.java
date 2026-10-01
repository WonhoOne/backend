package com.wonhoone.misterworld.api.error;

import java.util.Comparator;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AuthException.class)
    ResponseEntity<ApiError> auth(AuthException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiError.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    boolean required = "NotBlank".equals(error.getCode());
                    return new ApiError.FieldError(error.getField(),
                            required ? FieldErrorCode.REQUIRED : FieldErrorCode.OUT_OF_RANGE,
                            required ? "This field is required." : "This field exceeds its supported length.");
                })
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .distinct().toList();
        // Never include rejected values or binding exception messages: they may contain passwords.
        return ResponseEntity.unprocessableContent()
                .body(new ApiError("VALIDATION_FAILED", "Request fields are invalid.", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed() {
        return ResponseEntity.badRequest().body(ApiError.of("MALFORMED_REQUEST", "Request JSON is malformed."));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ApiError> parameter() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_QUERY_PARAMETER", "Request parameter is invalid."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected() {
        return ResponseEntity.internalServerError().body(ApiError.of("INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
