package com.wonhoone.misterworld.application.auth;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.api.error.AuthException;
import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.entity.UserAccountJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;
import com.wonhoone.misterworld.security.JwtTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.exception.ConstraintViolationException;

@Service
public class AuthService {
    private final UserAccountJpaRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final String dummyPasswordHash;

    public AuthService(UserAccountJpaRepository accounts, PasswordEncoder passwords, JwtTokenService tokens) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        // Unknown accounts still do a BCrypt comparison to reduce account enumeration by timing.
        this.dummyPasswordHash = passwords.encode("unused-login-comparison");
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String loginId = LoginIdNormalizer.normalize(request.loginId());
        if (accounts.existsByLoginId(loginId)) {
            throw duplicateLoginId();
        }
        String passwordHash = passwords.encode(request.password());
        try {
            var account = accounts.saveAndFlush(new UserAccountJpaEntity(loginId, passwordHash,
                    request.name(), request.address(), request.contact(), UserRole.CUSTOMER));
            return UserResponse.from(account);
        } catch (DataIntegrityViolationException exception) {
            // The unique key also arbitrates concurrent signups after the pre-check.
            // Other storage failures must remain INTERNAL_ERROR, not a misleading credential conflict.
            if (exception.getCause() instanceof ConstraintViolationException constraint
                    && constraint.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                throw duplicateLoginId();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String loginId = LoginIdNormalizer.normalize(request.loginId());
        var account = accounts.findByLoginId(loginId);
        String hash = account.map(UserAccountJpaEntity::getPasswordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwords.matches(request.password(), hash);
        if (account.isEmpty() || !passwordMatches) {
            throw new AuthException(401, "LOGIN_FAILED", "Login ID or password is incorrect.");
        }
        var user = account.orElseThrow();
        var token = tokens.issue(user.getId(), user.getRole());
        return new LoginResponse(token.value(), "Bearer", token.expiresIn(), UserResponse.from(user));
    }

    private AuthException duplicateLoginId() {
        return new AuthException(409, "LOGIN_ID_ALREADY_EXISTS", "Login ID is already in use.");
    }
}
