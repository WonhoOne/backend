package com.wonhoone.misterworld.application.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CanonicalLoginIdLengthValidator implements ConstraintValidator<ValidLoginId, String> {
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        // Unicode lowercase can expand the stored key beyond the raw request length.
        if (value == null || value.isBlank()) return true; // NotBlank owns required-field errors.
        String canonical = LoginIdNormalizer.normalize(value);
        return !canonical.isBlank() && canonical.length() <= 255;
    }
}
