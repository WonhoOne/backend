package com.wonhoone.misterworld.api.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    @Override public String toString() { return "LoginResponse[token redacted]"; }
}
