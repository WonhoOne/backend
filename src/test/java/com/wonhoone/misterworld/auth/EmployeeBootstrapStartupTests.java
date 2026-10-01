package com.wonhoone.misterworld.auth;

import com.wonhoone.misterworld.application.auth.EmployeeBootstrap;
import com.wonhoone.misterworld.domain.UserRole;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:employee-startup;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "employee.bootstrap.enabled=true",
        "employee.bootstrap.login-id= StartupAdmin ",
        "employee.bootstrap.password=test-only-bootstrap-password",
        "employee.bootstrap.name=Startup Employee",
        "employee.bootstrap.address=Seoul",
        "employee.bootstrap.contact=010"
})
@ActiveProfiles("test")
class EmployeeBootstrapStartupTests {
    @Autowired UserAccountJpaRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired EmployeeBootstrap bootstrap;

    @Test void enabledTestPropertiesProvisionEmployeeDuringApplicationStartup() {
        var employee = accounts.findByLoginId("startupadmin").orElseThrow();
        assertThat(employee.getRole()).isEqualTo(UserRole.EMPLOYEE);
        assertThat(passwords.matches("test-only-bootstrap-password", employee.getPasswordHash())).isTrue();
        bootstrap.run(new DefaultApplicationArguments(new String[0]));
        assertThat(accounts.count()).isEqualTo(1);
    }
}
