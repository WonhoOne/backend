package com.wonhoone.misterworld.application.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CanonicalLoginIdLengthValidator.class)
public @interface ValidLoginId {
    String message() default "Canonical login ID exceeds its storage length.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
