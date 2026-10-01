package com.wonhoone.misterworld.application.auth;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordByteLengthValidator implements ConstraintValidator<ValidPassword, String> {
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        // BCrypt consumes at most 72 bytes; reject rather than truncate multibyte passwords.
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
