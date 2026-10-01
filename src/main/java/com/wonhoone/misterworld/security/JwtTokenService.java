package com.wonhoone.misterworld.security;

import com.wonhoone.misterworld.domain.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.*;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class JwtTokenService {
    public static final String EXPIRED_ERROR = "access_token_expired";
    private final JwtEncoder encoder;
    private final SecretKeySpec key;
    private final long lifetime;
    private final Clock clock = Clock.systemUTC();

    public JwtTokenService(@Value("${security.jwt.secret:}") String secret,
                           @Value("${security.jwt.expires-in-seconds:3600}") long lifetime) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secret.isBlank() || bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must provide at least 32 UTF-8 bytes.");
        }
        if (lifetime <= 0) throw new IllegalStateException("JWT_EXPIRES_IN_SECONDS must be positive.");
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.lifetime = lifetime;
    }

    public IssuedToken issue(long accountId, UserRole role) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder()
                .subject(Long.toString(accountId))
                .claim("role", role.name())
                .issuedAt(now).expiresAt(now.plusSeconds(lifetime)).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), lifetime);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Nimbus verifies the signature before calling this validator; an untrusted exp cannot choose the error.
        decoder.setJwtValidator(this::validateClaims);
        return decoder;
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
        Instant now = clock.instant();
        if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                || jwt.getIssuedAt().isAfter(now)
                || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))) {
            return invalid("invalid_token");
        }
        try {
            if (Long.parseLong(jwt.getSubject()) <= 0) return invalid("invalid_token");
            UserRole.valueOf(jwt.getClaimAsString("role"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return invalid("invalid_token");
        }
        if (!jwt.getExpiresAt().isAfter(now)) return invalid(EXPIRED_ERROR);
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalid(String code) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, "Access token is invalid.", null));
    }

    public record IssuedToken(String value, long expiresIn) {
        @Override public String toString() { return "IssuedToken[token redacted]"; }
    }
}
