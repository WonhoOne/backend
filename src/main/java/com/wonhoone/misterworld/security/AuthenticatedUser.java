package com.wonhoone.misterworld.security;

import com.wonhoone.misterworld.domain.UserRole;
import org.springframework.security.oauth2.jwt.Jwt;

/** Future controllers can accept @AuthenticationPrincipal Jwt and pass this identity to application services. */
public record AuthenticatedUser(long id, UserRole role) {
    public static AuthenticatedUser from(Jwt verifiedJwt) {
        return new AuthenticatedUser(Long.parseLong(verifiedJwt.getSubject()),
                UserRole.valueOf(verifiedJwt.getClaimAsString("role")));
    }
}
