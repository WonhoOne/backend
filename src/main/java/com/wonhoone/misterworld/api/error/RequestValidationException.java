package com.wonhoone.misterworld.api.error;

import java.util.List;

public class RequestValidationException extends RuntimeException {
    private final List<ApiError.FieldError> fieldErrors;
    public RequestValidationException(List<ApiError.FieldError> fieldErrors) {
        super("Request fields are invalid.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }
    public List<ApiError.FieldError> fieldErrors() { return fieldErrors; }
}
