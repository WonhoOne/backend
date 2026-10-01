package com.wonhoone.misterworld.api.error;

import java.util.List;

public record ApiError(String code, String message, List<FieldError> fieldErrors) {
    public ApiError { fieldErrors = List.copyOf(fieldErrors); }
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, List.of());
    }
    public record FieldError(String field, FieldErrorCode code, String message) {}
}
