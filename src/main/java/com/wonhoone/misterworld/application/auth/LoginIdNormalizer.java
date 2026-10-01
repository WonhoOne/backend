package com.wonhoone.misterworld.application.auth;

import java.util.Locale;

public final class LoginIdNormalizer {
    private LoginIdNormalizer() {}
    public static String normalize(String loginId) {
        return loginId.trim().toLowerCase(Locale.ROOT);
    }
}
