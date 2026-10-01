package com.wonhoone.misterworld.auth;

import com.wonhoone.misterworld.application.auth.*;
import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.entity.UserAccountJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeProvisioningTests {
    @Autowired UserAccountJpaRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired Validator validator;
    @Autowired LegacyLoginIdCanonicalizer legacy;

    @BeforeEach void cleanAccounts() { accounts.deleteAll(); }

    @Test void disabledBootstrapCreatesNoEmployee() {
        bootstrap(false, "", "").run(arguments());
        assertThat(accounts.count()).isZero();
    }

    @Test void enabledBootstrapCreatesEmployeeWithEncodedPassword() {
        bootstrap(true, " ADMIN ", "password").run(arguments());
        var employee = accounts.findByLoginId("admin").orElseThrow();
        assertThat(employee.getRole()).isEqualTo(UserRole.EMPLOYEE);
        assertThat(employee.getPasswordHash()).isNotEqualTo("password");
        assertThat(passwords.matches("password", employee.getPasswordHash())).isTrue();
        assertThat(employee.getName()).isEqualTo("Employee");
        assertThat(employee.getAddress()).isEqualTo("Seoul");
        assertThat(employee.getContact()).isEqualTo("010");
    }

    @Test void bootstrapIsIdempotent() {
        var runner = bootstrap(true, " ADMIN ", "password");
        runner.run(arguments());
        var first = accounts.findByLoginId("admin").orElseThrow();
        String hash = first.getPasswordHash();
        runner.run(arguments());
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(accounts.findByLoginId("admin").orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @Test void existingCustomerIsNeverPromotedOrOverwrittenByBootstrap() {
        accounts.saveAndFlush(new UserAccountJpaEntity("admin", passwords.encode("original"), "Customer", "Old", "Old", UserRole.CUSTOMER));
        bootstrap(true, "ADMIN", "password").run(arguments());
        var existing = accounts.findByLoginId("admin").orElseThrow();
        assertThat(existing.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(passwords.matches("original", existing.getPasswordHash())).isTrue();
    }

    @Test void enabledBootstrapWithMissingConfigurationFailsClearlyWithoutCredentials() {
        assertThatThrownBy(() -> bootstrap(true, "", "").run(arguments()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMPLOYEE_LOGIN_ID", "EMPLOYEE_PASSWORD");
        assertThat(accounts.count()).isZero();
    }

    @Test void bootstrapRejectsPasswordsExceedingBcryptInputBound() {
        String raw = "가".repeat(25);
        assertThatThrownBy(() -> bootstrap(true, "admin", raw).run(arguments()))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(raw);
    }

    @Test void legacyLoginIdIsCanonicalizedWithoutChangingIdentityOrPassword() {
        var account = accounts.saveAndFlush(new UserAccountJpaEntity(" Customer01 ", "existing-hash", "A", "B", "C", UserRole.CUSTOMER));
        long id = account.getId();
        legacy.run(arguments());
        var normalized = accounts.findByLoginId("customer01").orElseThrow();
        assertThat(normalized.getId()).isEqualTo(id);
        assertThat(normalized.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test void legacyCanonicalCollisionStopsBeforeAnyAccountIsChanged() {
        accounts.saveAndFlush(new UserAccountJpaEntity("Admin", "hash", "A", "B", "C", UserRole.CUSTOMER));
        accounts.saveAndFlush(new UserAccountJpaEntity("ADMIN", "hash", "A", "B", "C", UserRole.EMPLOYEE));
        assertThatThrownBy(() -> legacy.run(arguments())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("collisions");
        assertThat(accounts.findByLoginId("Admin")).isPresent();
        assertThat(accounts.findByLoginId("ADMIN")).isPresent();
    }

    @Test void loginIdNormalizationUsesRootLocale() {
        var original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(LoginIdNormalizer.normalize(" ADMIN ")).isEqualTo("admin");
        } finally { java.util.Locale.setDefault(original); }
    }

    private EmployeeBootstrap bootstrap(boolean enabled, String loginId, String password) {
        return new EmployeeBootstrap(accounts, passwords, validator, enabled, loginId, password, "Employee", "Seoul", "010");
    }
    private DefaultApplicationArguments arguments() { return new DefaultApplicationArguments(new String[0]); }
}
