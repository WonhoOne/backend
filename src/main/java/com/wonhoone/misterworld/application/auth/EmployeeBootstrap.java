package com.wonhoone.misterworld.application.auth;

import com.wonhoone.misterworld.api.dto.SignupRequest;
import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.entity.UserAccountJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(1)
public class EmployeeBootstrap implements ApplicationRunner {
    private final UserAccountJpaRepository accounts;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final boolean enabled;
    private final SignupRequest profile;

    public EmployeeBootstrap(UserAccountJpaRepository accounts, PasswordEncoder passwords, Validator validator,
            @Value("${employee.bootstrap.enabled:false}") boolean enabled,
            @Value("${employee.bootstrap.login-id:}") String loginId,
            @Value("${employee.bootstrap.password:}") String password,
            @Value("${employee.bootstrap.name:}") String name,
            @Value("${employee.bootstrap.address:}") String address,
            @Value("${employee.bootstrap.contact:}") String contact) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.validator = validator;
        this.enabled = enabled;
        this.profile = new SignupRequest(loginId, password, name, address, contact);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        if (!validator.validate(profile).isEmpty()) {
            throw new IllegalStateException("Employee bootstrap requires valid EMPLOYEE_LOGIN_ID, EMPLOYEE_PASSWORD, EMPLOYEE_NAME, EMPLOYEE_ADDRESS and EMPLOYEE_CONTACT.");
        }
        String loginId = LoginIdNormalizer.normalize(profile.loginId());
        if (accounts.existsByLoginId(loginId)) return;
        accounts.saveAndFlush(new UserAccountJpaEntity(loginId, passwords.encode(profile.password()),
                profile.name(), profile.address(), profile.contact(), UserRole.EMPLOYEE));
    }
}
