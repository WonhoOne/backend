package com.wonhoone.misterworld.infrastructure.sms.solapi;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.HexFormat;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class SolapiAuthHeaderFactory {
    private final String apiKey;
    private final String apiSecret;
    private final Clock clock;
    private final Supplier<String> salts;
    public SolapiAuthHeaderFactory(String apiKey, String apiSecret, Clock clock) {
        this(apiKey, apiSecret, clock, randomSalts());
    }
    public SolapiAuthHeaderFactory(String apiKey, String apiSecret, Clock clock, Supplier<String> salts) {
        this.apiKey = apiKey; this.apiSecret = apiSecret; this.clock = clock; this.salts = salts;
    }
    private static Supplier<String> randomSalts() {
        var random = new SecureRandom();
        return () -> {
            byte[] bytes = new byte[16]; random.nextBytes(bytes);
            return HexFormat.of().formatHex(bytes);
        };
    }
    public String create() {
        String date = clock.instant().toString();
        String salt = salts.get();
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = HexFormat.of().formatHex(mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8)));
            return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("SOLAPI request signing failed");
        }
    }
}
