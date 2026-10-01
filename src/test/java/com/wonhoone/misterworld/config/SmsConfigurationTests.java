package com.wonhoone.misterworld.config;

import com.wonhoone.misterworld.application.port.SmsSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class SmsConfigurationTests {
    final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(SmsDeliveryConfig.class)
            .withPropertyValues("sms.delivery.enabled=false", "sms.delivery.poll-delay-ms=10000", "sms.delivery.batch-size=50",
                    "sms.retry.initial-seconds=30", "sms.retry.max-seconds=1800", "sms.provider=solapi",
                    "sms.solapi.api-key=", "sms.solapi.api-secret=", "sms.solapi.sender-number=",
                    "sms.solapi.base-url=https://api.solapi.com", "sms.solapi.connect-timeout-seconds=5",
                    "sms.solapi.request-timeout-seconds=10");
    @Test void disabledDeliveryStartsWithoutCredentialsOrSender() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(SmsSender.class);
            assertThat(context.getBean(SmsProperties.class).delivery().enabled()).isFalse();
        });
    }
    @Test void enabledDeliveryMissingCredentialsFailsAtStartupSafely() {
        runner.withPropertyValues("sms.delivery.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage(
                    "Enabled SOLAPI requires API key, API secret and registered sender number");
        });
    }
    @Test void unsupportedEnabledProviderFailsAtStartup() {
        runner.withPropertyValues("sms.delivery.enabled=true", "sms.provider=unconfigured").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Enabled SMS provider must be solapi");
        });
    }
    @Test void invalidRetryBoundsFailAtStartup() {
        runner.withPropertyValues("sms.retry.initial-seconds=60", "sms.retry.max-seconds=30")
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void completeEnabledConfigCreatesRealAdapterWithoutCallingProvider() {
        runner.withPropertyValues("sms.delivery.enabled=true", "sms.solapi.api-key=dummy-key",
                "sms.solapi.api-secret=dummy-secret", "sms.solapi.sender-number=010-0000-0000").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SmsSender.class);
            assertThat(context.getBean(SmsProperties.class).toString()).doesNotContain("dummy-key", "dummy-secret", "010-0000-0000");
        });
    }
    @Test void insecureProviderOriginIsRejectedAtStartup() {
        runner.withPropertyValues("sms.delivery.enabled=true", "sms.solapi.api-key=dummy-key",
                "sms.solapi.api-secret=dummy-secret", "sms.solapi.sender-number=010-0000-0000",
                "sms.solapi.base-url=http://api.solapi.com").run(context -> assertThat(context).hasFailed());
    }
}
