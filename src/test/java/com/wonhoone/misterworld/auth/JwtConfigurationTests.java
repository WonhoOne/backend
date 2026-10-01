package com.wonhoone.misterworld.auth;

import com.wonhoone.misterworld.security.JwtTokenService;
import com.wonhoone.misterworld.domain.UserRole;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtConfigurationTests {
    @Test void defaultStartupRequiresAnExternalSecret() {
        assertThatThrownBy(() -> new JwtTokenService("", 3600)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }
    @Test void undersizedSecretIsRejectedWithoutEchoingIt() {
        assertThatThrownBy(() -> new JwtTokenService("sensitive-value", 3600)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("sensitive-value");
    }
    @Test void lifetimeMustBePositive() {
        assertThatThrownBy(() -> new JwtTokenService("test-only-secret-32-bytes-long-key", 0))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_EXPIRES_IN_SECONDS");
    }
    @Test void configuredLifetimeIsReportedInSeconds() {
        var tokens = new JwtTokenService("test-only-secret-32-bytes-long-key", 600);
        assertThat(tokens.issue(1, UserRole.CUSTOMER).expiresIn()).isEqualTo(600);
    }
}
