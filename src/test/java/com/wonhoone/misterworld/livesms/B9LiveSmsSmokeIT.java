package com.wonhoone.misterworld.livesms;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.application.auth.AuthService;
import com.wonhoone.misterworld.application.demo.*;
import com.wonhoone.misterworld.application.port.*;
import com.wonhoone.misterworld.application.reservation.ReservationCommandService;
import com.wonhoone.misterworld.application.sms.SmsDeliveryPoller;
import com.wonhoone.misterworld.application.time.BusinessDateProvider;
import com.wonhoone.misterworld.domain.*;
import com.wonhoone.misterworld.infrastructure.persistence.entity.SmsDeliveryStatus;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** PAID EXTERNAL ACTION. Never run during A2.1 or CI. One test, one consented handset. */
@SpringBootTest(properties = {"demo.provisioning.enabled=false", "employee.bootstrap.enabled=false",
        "sms.solapi.connect-timeout-seconds=5", "sms.solapi.request-timeout-seconds=10"})
@ContextConfiguration(initializers = B9LiveSmsSmokeIT.OperatorPreflight.class)
class B9LiveSmsSmokeIT {
    @MockitoBean SmsDeliveryPoller poller; // Removes scheduled network retry; immediate listener remains real.
    @MockitoSpyBean(name = "solapiSmsSender") SmsSender sender;
    @Autowired DemoScenarioProvisioner provisioner;
    @Autowired BusinessDateProvider date;
    @Autowired AuthService auth;
    @Autowired ReservationCommandService commands;
    @Autowired TourScheduleJpaRepository schedules;
    @Autowired ReservationJpaRepository reservations;
    @Autowired SmsConfirmationEventJpaRepository events;
    @Autowired SmsConfirmationRecipientJpaRepository recipients;
    @Autowired JdbcTemplate jdbc;

    public static class OperatorPreflight implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override public void initialize(ConfigurableApplicationContext context) {
            var env = System.getenv();
            LiveSmsSafety.environment(env); // Before any Spring bean, Flyway or provider construction.
            try (var connection = DriverManager.getConnection(env.get("DB_URL"), env.get("DB_USERNAME"), env.get("DB_PASSWORD"))) {
                LiveSmsSafety.freshDatabase(connection, env.get("B9_LIVE_SMS_EXPECTED_DATABASE"));
            } catch (java.sql.SQLException failure) { throw LiveSmsSafety.stopped("database preflight connection failed"); }
            var pinned = new HashMap<String, Object>();
            pinned.put("sms.delivery.enabled", "true"); pinned.put("sms.provider", "solapi");
            pinned.put("demo.provisioning.enabled", "false"); pinned.put("employee.bootstrap.enabled", "false");
            // The app must use precisely the DB and provider settings checked above, regardless of inherited JVM overrides.
            pinned.put("spring.datasource.url", env.get("DB_URL"));
            pinned.put("spring.datasource.username", env.get("DB_USERNAME"));
            pinned.put("spring.datasource.password", env.get("DB_PASSWORD"));
            pinned.put("security.jwt.secret", env.get("JWT_SECRET"));
            pinned.put("sms.solapi.api-key", env.get("SOLAPI_API_KEY"));
            pinned.put("sms.solapi.api-secret", env.get("SOLAPI_API_SECRET"));
            pinned.put("sms.solapi.sender-number", env.get("SOLAPI_SENDER_NUMBER"));
            pinned.put("sms.solapi.base-url", "https://api.solapi.com");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("operatorLiveSmoke", pinned));
        }
    }

    @Test void oneConfirmationUsesRealAfterCommitSolapiPath() throws Exception {
        // No cleanup, seed, retry or send until all preflight gates succeeded. Fresh DB stays for operator inspection.
        for (String table : List.of("user_account", "tour_product", "tour_schedule", "tour_reservation",
                "sms_confirmation_event", "sms_confirmation_recipient"))
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class), "Fresh business tables required");
        var sends = new AtomicInteger();
        doAnswer(invocation -> {
            if (sends.incrementAndGet() != 1) throw LiveSmsSafety.stopped("second provider call blocked");
            return invocation.callRealMethod();
        }).when(sender).send(any(SmsMessage.class));
        var prices = Arrays.stream(TourStyle.values()).map(style ->
                new TourProductWriteRequest.StylePrice(style, 101L, "KRW")).toList();
        var start = date.today().plusDays(30);
        provisioner.provision(new DemoScenarioManifest(1, List.of(new DemoScenarioManifest.Product("live-test",
                Theme.GOLF_CHALLENGE, "B9 Live SMS Smoke Product", "Clearly test-only live SMS smoke fixture", prices)),
                List.of(new DemoScenarioManifest.Schedule("live-test", start, start.plusDays(4)))),
                System.getenv("B9_LIVE_SMS_EXPECTED_DATABASE"));
        var customer = auth.signup(new SignupRequest("b9-live-test-only", "b9-live-test-only-password",
                "B9 Test Customer", "Test-only address", System.getenv("B9_SMS_RECIPIENT")));
        var schedule = schedules.findAllByOrderByStartDateAscIdAsc().getFirst();
        var result = commands.create(customer.id(), new ReservationCreateRequest(schedule.getId(), 3,
                new ReservationCreateRequest.Configuration(TourStyle.GRAND, HotelOption.HOTEL_4_STAR,
                        TransportOption.PREMIUM_VAN_10, MealOption.LOCAL_RESTAURANT, List.of())));
        assertTrue(result.scheduleJustConfirmed(), "Expected first confirmation");
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            if (recipients.count() == 1 && recipients.findAll().getFirst().getAttemptCount() >= 1) break;
            Thread.sleep(100);
        }
        assertTrue(schedules.findById(schedule.getId()).orElseThrow().isConfirmed());
        assertEquals(1, reservations.count()); assertEquals(1, events.count()); assertEquals(1, recipients.count());
        var recipient = recipients.findAll().getFirst();
        assertEquals(SmsDeliveryStatus.SENT, recipient.getStatus(), "Provider acceptance failed; no automatic retry");
        assertEquals(1, recipient.getAttemptCount()); assertNotNull(recipient.getSentAt());
        assertNull(recipient.getNextAttemptAt()); assertEquals(1, sends.get());
        // Adapter permits an omitted provider ID. Never put a supplied ID/phone/provider body in assertion output.
        if (recipient.getProviderMessageId() != null) assertTrue(!recipient.getProviderMessageId().isBlank());
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/b9-live-sms-smoke.txt"), "timestamp UTC=" + Instant.now()
                + "\nMySQL version=" + jdbc.queryForObject("SELECT VERSION()", String.class)
                + "\nevents=1\nrecipients=1\nattempts=1\nPROVIDER_ACCEPTED=YES\nHANDSET_RECEIVED=MANUAL_PENDING\n");
    }
}
