package com.wonhoone.misterworld.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sms")
public record SmsProperties(Delivery delivery, Retry retry, String provider, Solapi solapi) {
    public record Delivery(boolean enabled, long pollDelayMs, int batchSize) {}
    public record Retry(long initialSeconds, long maxSeconds) {}
    public record Solapi(String apiKey, String apiSecret, String senderNumber, String baseUrl,
                         long connectTimeoutSeconds, long requestTimeoutSeconds) {
        @Override public String toString() { return "Solapi[redacted]"; }
    }
    public SmsProperties {
        if (delivery.pollDelayMs() <= 0 || delivery.batchSize() <= 0)
            throw new IllegalArgumentException("SMS poll delay and batch size must be positive");
        if (retry.initialSeconds() <= 0 || retry.maxSeconds() < retry.initialSeconds())
            throw new IllegalArgumentException("SMS retry delays must be positive and ordered");
        if (solapi.connectTimeoutSeconds() <= 0 || solapi.requestTimeoutSeconds() <= 0)
            throw new IllegalArgumentException("SMS HTTP timeouts must be positive");
        if (delivery.enabled()) {
            if (!"solapi".equals(provider)) throw new IllegalArgumentException("Enabled SMS provider must be solapi");
            if (blank(solapi.apiKey()) || blank(solapi.apiSecret()) || blank(solapi.senderNumber()))
                throw new IllegalArgumentException("Enabled SOLAPI requires API key, API secret and registered sender number");
            if (!solapi.senderNumber().replaceAll("[\\s().-]", "").matches("[0-9]+"))
                throw new IllegalArgumentException("SOLAPI sender number must normalize to digits");
            URI base;
            try { base = URI.create(solapi.baseUrl()); }
            catch (RuntimeException invalid) { throw new IllegalArgumentException("SOLAPI base URL is invalid"); }
            if (!"https".equals(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                    || base.getQuery() != null || base.getFragment() != null
                    || !(base.getPath().isEmpty() || "/".equals(base.getPath())))
                throw new IllegalArgumentException("SOLAPI base URL must be an HTTPS origin");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
