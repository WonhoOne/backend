package com.wonhoone.misterworld.auth;

import com.wonhoone.misterworld.api.dto.SignupRequest;
import com.wonhoone.misterworld.api.error.AuthException;
import com.wonhoone.misterworld.application.auth.AuthService;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;
import com.wonhoone.misterworld.security.JwtTokenService;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class AuthServiceFailureTests {
    @Test void simultaneousUniqueKeyConflictReturnsDuplicateLoginId() {
        var accounts = mock(UserAccountJpaRepository.class);
        var violation = new ConstraintViolationException("unique conflict", new SQLException(),
                ConstraintViolationException.ConstraintKind.UNIQUE, "uk_user_account_login_id");
        when(accounts.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("storage failure", violation));
        var auth = new AuthService(accounts, new BCryptPasswordEncoder(), mock(JwtTokenService.class));
        assertThatThrownBy(() -> auth.signup(request())).isInstanceOfSatisfying(AuthException.class, error -> {
            assertThat(error.status()).isEqualTo(409);
            assertThat(error.code()).isEqualTo("LOGIN_ID_ALREADY_EXISTS");
        });
    }

    @Test void otherStorageFailureIsNotMisreportedAsDuplicateLoginId() {
        var accounts = mock(UserAccountJpaRepository.class);
        var violation = new DataIntegrityViolationException("internal storage detail");
        when(accounts.saveAndFlush(any())).thenThrow(violation);
        var auth = new AuthService(accounts, new BCryptPasswordEncoder(), mock(JwtTokenService.class));
        assertThatThrownBy(() -> auth.signup(request())).isSameAs(violation);
    }

    private SignupRequest request() {
        return new SignupRequest("customer", "password", "Customer", "Seoul", "010");
    }
}
