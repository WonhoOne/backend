package com.wonhoone.misterworld.application.auth;

import java.util.HashSet;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.wonhoone.misterworld.infrastructure.persistence.repository.UserAccountJpaRepository;

@Component
@Order(0)
public class LegacyLoginIdCanonicalizer implements ApplicationRunner {
    private final UserAccountJpaRepository accounts;
    public LegacyLoginIdCanonicalizer(UserAccountJpaRepository accounts) { this.accounts = accounts; }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        var existing = accounts.findAll();
        var keys = new HashSet<String>();
        // Check the complete plan before updating any row, preserving identities and password hashes.
        for (var account : existing) {
            String key = LoginIdNormalizer.normalize(account.getLoginId());
            if (key.isBlank() || key.length() > 255 || !keys.add(key)) {
                throw new IllegalStateException("Existing login IDs cannot be canonicalized safely; resolve credential collisions before startup.");
            }
        }
        for (var account : existing) {
            account.canonicalizeLoginId(LoginIdNormalizer.normalize(account.getLoginId()));
        }
        accounts.flush();
    }
}
