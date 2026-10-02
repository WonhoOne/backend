package com.wonhoone.misterworld.livesms;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveSmsSafetyTests {
    Map<String, String> configured() {
        var env = new HashMap<String, String>();
        for (var key : List.of("DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "SOLAPI_API_KEY", "SOLAPI_API_SECRET"))
            env.put(key, "test-only-value");
        env.put("DB_URL", "jdbc:mysql://localhost/test_only"); env.put("B9_LIVE_SMS_EXPECTED_DATABASE", "test_only");
        env.put("B9_SMS_RECIPIENT", "010-0000-0000"); env.put("SOLAPI_SENDER_NUMBER", "010-0000-0000");
        env.put("B9_LIVE_SMS_ACK", LiveSmsSafety.ACK); env.put("SMS_DELIVERY_ENABLED", "true"); env.put("SMS_PROVIDER", "solapi");
        return env;
    }
    @Test void explicitAcknowledgementRequiredBeforeDatabaseOrProvider() {
        var env = configured(); env.put("B9_LIVE_SMS_ACK", "true");
        assertThatThrownBy(() -> LiveSmsSafety.environment(env)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("acknowledgement").hasMessageNotContaining("test-only-value");
    }
    @ParameterizedTest @ValueSource(strings = {"B9_SMS_RECIPIENT", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET",
            "SOLAPI_API_KEY", "SOLAPI_API_SECRET", "SOLAPI_SENDER_NUMBER", "B9_LIVE_SMS_EXPECTED_DATABASE"})
    void everyRequiredEnvironmentIsChecked(String key) {
        var env = configured(); env.remove(key);
        assertThatThrownBy(() -> LiveSmsSafety.environment(env)).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"SMS_DELIVERY_ENABLED", "SMS_PROVIDER", "DB_URL", "B9_SMS_RECIPIENT", "SOLAPI_BASE_URL"})
    void unsafeSettingsAreRejectedWithoutValueDisclosure(String key) {
        var env = configured(); env.put(key, "private-test-only");
        assertThatThrownBy(() -> LiveSmsSafety.environment(env)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("private-test-only");
    }
    @Test void completeExplicitEnvironmentPassesWithoutSending() { LiveSmsSafety.environment(configured()); }
    @Test void emptyMatchingMySqlDatabasePasses() throws Exception { LiveSmsSafety.freshDatabase(connection("test_only", 0), "test_only"); }
    @Test void mismatchedDatabaseRejected() throws Exception {
        var connection = connection("other", 0);
        assertThatThrownBy(() -> LiveSmsSafety.freshDatabase(connection, "test_only")).isInstanceOf(IllegalStateException.class);
    }
    @Test void existingDatabaseRefusedBeforeFlywayEvenWithEmptyBusinessTables() throws Exception {
        var connection = connection("test_only", 1);
        assertThatThrownBy(() -> LiveSmsSafety.freshDatabase(connection, "test_only")).isInstanceOf(IllegalStateException.class);
    }
    Connection connection(String database, long tables) throws Exception {
        var connection = mock(Connection.class); var statement = mock(Statement.class);
        var identity = mock(ResultSet.class); var count = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT DATABASE(), VERSION(), @@version_comment")).thenReturn(identity);
        when(identity.next()).thenReturn(true); when(identity.getString(1)).thenReturn(database);
        when(identity.getString(2)).thenReturn("8.4.11"); when(identity.getString(3)).thenReturn("MySQL Community Server");
        when(statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")).thenReturn(count);
        when(count.next()).thenReturn(true); when(count.getLong(1)).thenReturn(tables);
        return connection;
    }
}
