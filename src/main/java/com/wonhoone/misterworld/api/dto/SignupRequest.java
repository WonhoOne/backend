package com.wonhoone.misterworld.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.wonhoone.misterworld.application.auth.ValidPassword;
import com.wonhoone.misterworld.application.auth.ValidLoginId;

public record SignupRequest(
        @NotBlank @Size(max = 255) @ValidLoginId String loginId,
        @NotBlank @ValidPassword String password,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String address,
        @NotBlank @Size(max = 255) String contact) {
    // Records otherwise include credentials in their generated toString.
    @Override public String toString() { return "SignupRequest[credentials redacted]"; }
}
