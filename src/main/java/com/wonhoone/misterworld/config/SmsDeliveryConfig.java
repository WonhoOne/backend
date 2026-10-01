package com.wonhoone.misterworld.config;

import com.wonhoone.misterworld.application.port.SmsSender;
import com.wonhoone.misterworld.application.sms.SmsRetryPolicy;
import com.wonhoone.misterworld.infrastructure.sms.solapi.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsDeliveryConfig {
    @Bean("smsClock") public Clock smsClock() { return Clock.systemUTC(); }
    @Bean public SmsRetryPolicy smsRetryPolicy(SmsProperties properties) {
        return new SmsRetryPolicy(properties.retry().initialSeconds(), properties.retry().maxSeconds());
    }

    @Configuration
    @ConditionalOnProperty(name = "sms.delivery.enabled", havingValue = "true")
    @EnableScheduling
    static class EnabledDeliveryConfig {
        @Bean("smsDeliveryExecutor")
        ThreadPoolTaskExecutor smsDeliveryExecutor() {
            var executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(2); executor.setMaxPoolSize(2); executor.setQueueCapacity(50);
            executor.setThreadNamePrefix("sms-delivery-");
            // Rejection never runs network I/O on the reservation thread. DB polling recovers it.
            return executor;
        }
        @Bean
        @ConditionalOnProperty(name = "sms.provider", havingValue = "solapi")
        SmsSender solapiSmsSender(SmsProperties properties, @Qualifier("smsClock") Clock clock) {
            var solapi = properties.solapi();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(solapi.connectTimeoutSeconds())).build();
            return new SolapiSmsSender(client, URI.create(solapi.baseUrl()), solapi.senderNumber(),
                    new SolapiAuthHeaderFactory(solapi.apiKey(), solapi.apiSecret(), clock),
                    Duration.ofSeconds(solapi.requestTimeoutSeconds()));
        }
    }
}
