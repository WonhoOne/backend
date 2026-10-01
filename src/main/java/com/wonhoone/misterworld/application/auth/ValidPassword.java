package com.wonhoone.misterworld.application.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordByteLengthValidator.class)
public @interface ValidPassword {
    String message() default "Password exceeds the supported UTF-8 length.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
