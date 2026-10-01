package com.wonhoone.misterworld.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.wonhoone.misterworld.application.auth.ValidPassword;
import com.wonhoone.misterworld.application.auth.ValidLoginId;

public record LoginRequest(
        @NotBlank @Size(max = 255) @ValidLoginId String loginId,
        @NotBlank @ValidPassword String password) {
    @Override public String toString() { return "LoginRequest[credentials redacted]"; }
}
