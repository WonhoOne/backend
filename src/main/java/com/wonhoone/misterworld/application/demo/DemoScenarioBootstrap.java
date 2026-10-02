package com.wonhoone.misterworld.application.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@EnableConfigurationProperties(DemoProvisioningProperties.class)
public class DemoScenarioBootstrap implements ApplicationRunner {
    private final DemoProvisioningProperties properties;
    private final DemoScenarioReader reader;
    private final DemoScenarioProvisioner provisioner;
    public DemoScenarioBootstrap(DemoProvisioningProperties properties, DemoScenarioReader reader,
                                 DemoScenarioProvisioner provisioner) {
        this.properties = properties; this.reader = reader; this.provisioner = provisioner;
    }
    @Override public void run(ApplicationArguments arguments) {
        if (!properties.enabled()) return;
        properties.requireEnabledConfiguration();
        provisioner.provision(reader.read(properties.scenarioFile()), properties.expectedDatabase());
    }
}
