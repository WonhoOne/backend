package com.wonhoone.misterworld.infrastructure.sms.solapi;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SolapiAuthHeaderFactoryTests {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC);
    @Test void deterministicHmacSha256SignsDatePlusSaltAndNeverIncludesRawSecret() {
        var factory = new SolapiAuthHeaderFactory("dummy-key", "dummy-secret", clock,
                () -> "0123456789abcdef0123456789abcdef");
        assertThat(factory.create()).isEqualTo("HMAC-SHA256 apiKey=dummy-key, date=2026-10-01T00:00:00Z, "
                + "salt=0123456789abcdef0123456789abcdef, "
                + "signature=d8cde6e67557dd85ef38baa66cbf91d393869f995e1952f96cc67f63b5549e71")
                .doesNotContain("dummy-secret");
    }
    @Test void everyProductionRequestUsesNewRandomSaltEvenAtSameTime() {
        var factory = new SolapiAuthHeaderFactory("dummy-key", "dummy-secret", clock);
        assertThat(factory.create()).isNotEqualTo(factory.create());
        assertThat(factory.create()).containsPattern("salt=[0-9a-f]{32}, signature=[0-9a-f]{64}$");
    }
}
