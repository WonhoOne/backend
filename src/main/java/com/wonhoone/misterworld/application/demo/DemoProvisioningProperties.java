package com.wonhoone.misterworld.application.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("demo.provisioning")
public record DemoProvisioningProperties(boolean enabled, String scenarioFile, String expectedDatabase) {
    public void requireEnabledConfiguration() {
        if (scenarioFile == null || scenarioFile.isBlank())
            throw new IllegalStateException("Demo provisioning requires DEMO_SCENARIO_FILE");
        if (expectedDatabase == null || expectedDatabase.isBlank())
            throw new IllegalStateException("Demo provisioning requires DEMO_EXPECTED_DATABASE");
    }
}
