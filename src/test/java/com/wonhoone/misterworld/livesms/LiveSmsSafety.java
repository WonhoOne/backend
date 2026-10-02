package com.wonhoone.misterworld.livesms;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Operator preflight, test scope only. Diagnostics identify gates, never supplied values. */
public final class LiveSmsSafety {
    public static final String ACK = "I_UNDERSTAND_THIS_SENDS_ONE_REAL_MESSAGE";
    private LiveSmsSafety() {}
    public static void environment(Map<String, String> env) {
        if (!ACK.equals(env.get("B9_LIVE_SMS_ACK"))) throw stopped("explicit paid-send acknowledgement required");
        for (String key : List.of("B9_SMS_RECIPIENT", "B9_LIVE_SMS_EXPECTED_DATABASE", "DB_URL", "DB_USERNAME",
                "DB_PASSWORD", "JWT_SECRET", "SOLAPI_API_KEY", "SOLAPI_API_SECRET", "SOLAPI_SENDER_NUMBER")) {
            if (env.get(key) == null || env.get(key).isBlank()) throw stopped("required environment missing: " + key);
        }
        if (!"true".equals(env.get("SMS_DELIVERY_ENABLED")) || !"solapi".equals(env.get("SMS_PROVIDER")))
            throw stopped("explicit SMS_DELIVERY_ENABLED=true and SMS_PROVIDER=solapi required");
        if (!env.get("DB_URL").startsWith("jdbc:mysql:")) throw stopped("actual disposable MySQL required");
        if (env.get("SOLAPI_BASE_URL") != null && !"https://api.solapi.com".equals(env.get("SOLAPI_BASE_URL")))
            throw stopped("official SOLAPI origin required");
        for (String key : List.of("B9_SMS_RECIPIENT", "SOLAPI_SENDER_NUMBER")) {
            if (!env.get(key).replaceAll("[\\s().-]", "").matches("[0-9]+")) throw stopped("phone normalization failed");
        }
    }
    public static void freshDatabase(Connection connection, String expected) throws SQLException {
        try (var sql = connection.createStatement()) {
            try (var rs = sql.executeQuery("SELECT DATABASE(), VERSION(), @@version_comment")) {
                if (!rs.next() || expected == null || expected.isBlank() || !expected.equals(rs.getString(1))
                        || !rs.getString(2).startsWith("8.")
                        || rs.getString(3).toLowerCase(java.util.Locale.ROOT).contains("mariadb"))
                    throw stopped("database identity or MySQL 8 mismatch");
            }
            // Stronger than business-row emptiness: BEFORE Flyway/startup runners, require zero tables.
            try (var rs = sql.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
                if (!rs.next() || rs.getLong(1) != 0) throw stopped("fresh empty disposable database required");
            }
        }
    }
    public static IllegalStateException stopped(String gate) { return new IllegalStateException("Live SMS STOP: " + gate); }
}
