package com.wonhoone.misterworld.mysql;

import com.wonhoone.misterworld.api.dto.*;
import com.wonhoone.misterworld.application.auth.*;
import com.wonhoone.misterworld.application.port.SmsSender;
import com.wonhoone.misterworld.domain.*;
import jakarta.validation.Validator;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.servlet.Filter;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MySqlRuntimeMySqlIT extends MySqlIntegrationSupport {
    @Autowired com.wonhoone.misterworld.application.demo.DemoScenarioProvisioner demo;
    @Autowired com.wonhoone.misterworld.application.tour.TourScheduleQueryService scheduleQueries;
    @Autowired Flyway flyway;
    @Autowired ApplicationContext context;
    @Autowired PasswordEncoder passwords;
    @Autowired Validator validator;
    @Autowired AuthService auth;
    @Autowired JwtDecoder decoder;
    @Autowired WebApplicationContext web;
    @Autowired ObjectMapper json;

    @Test void freshMySqlAppliesV1ThroughV4AndHibernateValidates() throws Exception {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank", String.class))
                .containsExactly("1", "2", "3", "4");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=0", Long.class)).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(context.getBeansOfType(SmsSender.class)).isEmpty();
        var facts = jdbc.queryForMap("""
                SELECT VERSION() AS version, @@version_comment AS version_comment,
                  @@transaction_isolation AS default_isolation, @@time_zone AS session_time_zone,
                  @@global.time_zone AS global_time_zone, @@system_time_zone AS system_time_zone,
                  @@sql_mode AS sql_mode, @@collation_database AS collation
                """);
        recordEvidence("Runtime " + facts + "; JVM zone=" + ZoneId.systemDefault());
        assertThat(facts.get("version").toString()).startsWith("8.");
        assertThat(facts.get("default_isolation")).isEqualTo("REPEATABLE-READ");
        assertThat(facts.get("sql_mode").toString()).contains("STRICT_TRANS_TABLES");
    }

    private com.wonhoone.misterworld.application.demo.DemoScenarioManifest demoManifest() {
        var stylePrices = java.util.Arrays.stream(TourStyle.values()).map(style ->
                new TourProductWriteRequest.StylePrice(style, 101L, "KRW")).toList();
        return new com.wonhoone.misterworld.application.demo.DemoScenarioManifest(1,
                List.of(new com.wonhoone.misterworld.application.demo.DemoScenarioManifest.Product("test-only",
                        Theme.GOLF_CHALLENGE, "B9 Test Product", "Clearly test-only description", stylePrices)),
                List.of(new com.wonhoone.misterworld.application.demo.DemoScenarioManifest.Schedule("test-only",
                        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5))));
    }

    @Test void externalDemoProvisioningIsReservableUnconfirmedAndOneShotOnMySql() {
        demo.provision(demoManifest(), "misterworld");
        assertThat(products.count()).isEqualTo(1); assertThat(prices.count()).isEqualTo(3);
        assertThat(scheduleQueries.list(null)).singleElement().satisfies(schedule -> {
            assertThat(schedule.reservable()).isTrue(); assertThat(schedule.recruitment().confirmed()).isFalse();
        });
        assertThat(accounts.count()).isZero(); assertThat(events.count()).isZero(); assertThat(recipients.count()).isZero();
        assertThatThrownBy(() -> demo.provision(demoManifest(), "misterworld")).isInstanceOf(IllegalStateException.class);
        assertThat(products.count()).isEqualTo(1);
    }

    @Test void laterSchedulePersistenceFailureRollsBackDemoCatalogOnMySql() {
        var proxy = (org.springframework.aop.framework.Advised) schedules;
        org.aopalliance.intercept.MethodInterceptor failure = call -> {
            if (call.getMethod().getName().equals("save")) throw new IllegalStateException("B9 test-only later schedule failure");
            return call.proceed();
        };
        proxy.addAdvice(0, failure);
        try {
            assertThatThrownBy(() -> demo.provision(demoManifest(), "misterworld")).isInstanceOf(RuntimeException.class);
            assertThat(products.count()).isZero(); assertThat(prices.count()).isZero(); assertThat(schedules.count()).isZero();
        } finally { proxy.removeAdvice(failure); }
    }

    @Test void schemaUsesInnoDbCompatibleTypesAndRequiredKeys() throws Exception {
        var tables = List.of("user_account", "tour_product", "tour_product_style_price", "tour_schedule", "inventory",
                "tour_reservation", "tour_reservation_extra_option", "sms_confirmation_event", "sms_confirmation_recipient");
        for (String table : tables) {
            assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",
                    String.class, table)).isEqualTo("InnoDB");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name=? AND constraint_type='PRIMARY KEY'",
                    Long.class, table)).isEqualTo(1);
            recordEvidence("DDL " + table + " " + jdbc.queryForMap("SHOW CREATE TABLE " + table).get("Create Table"));
        }
        assertColumn("inventory", "quantity", "bigint", "NO");
        assertColumn("tour_product_style_price", "amount", "bigint", "NO");
        assertColumn("user_account", "login_id", "varchar", "NO");
        assertColumn("tour_schedule", "confirmed", "tinyint", "NO");
        assertColumn("sms_confirmation_event", "created_at", "timestamp", "NO");
        assertColumn("sms_confirmation_recipient", "next_attempt_at", "timestamp", "YES");
        assertColumn("sms_confirmation_recipient", "sent_at", "timestamp", "YES");
        assertThat(jdbc.queryForList("SELECT datetime_precision FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('sms_confirmation_event','sms_confirmation_recipient') AND data_type='timestamp'", Integer.class))
                .containsExactlyInAnyOrder(6, 6, 6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()", Long.class))
                .isEqualTo(8);
    }

    @Test void inventoryCatalogContainsExactlyFourSeededAggregates() {
        assertThat(jdbc.queryForList("SELECT item_type FROM inventory ORDER BY item_type", String.class))
                .containsExactly("COUPLE_TSHIRT", "GINSENG_GIFT", "GOLF_BALL", "SCARF");
        assertThat(jdbc.queryForList("SELECT quantity FROM inventory", Long.class)).containsOnly(0L).hasSize(4);
    }

    @Test void mySqlEnforcesCanonicalCheckConstraintsAndSmsStates() {
        var customer = customer("constraints");
        var schedule = futureSchedule();
        var reservation = commands.create(customer.getId(), request(schedule.getId(), 2)).response();
        var recipient = recipient(schedule.getId(), customer.getId(), Instant.parse("2026-10-01T00:00:00Z"));
        rejected(3819, "UPDATE user_account SET role='INVALID' WHERE id=?", customer.getId());
        rejected(3819, "UPDATE inventory SET quantity=-1 WHERE item_type='SCARF'");
        rejected(3819, "UPDATE tour_product SET theme='INVALID' WHERE id=?", schedule.getTourProduct().getId());
        rejected(3819, "UPDATE tour_reservation SET participant_count=0 WHERE id=?", reservation.id());
        rejected(3819, "UPDATE tour_reservation SET currency='USD' WHERE id=?", reservation.id());
        rejected(3819, "UPDATE sms_confirmation_recipient SET status='INVALID' WHERE id=?", recipient.getId());
        rejected(3819, "UPDATE sms_confirmation_recipient SET next_attempt_at=NULL WHERE id=?", recipient.getId());
        rejected(3819, "UPDATE sms_confirmation_recipient SET sent_at=CURRENT_TIMESTAMP(6) WHERE id=?", recipient.getId());
        rejected(3819, "UPDATE sms_confirmation_recipient SET status='SENT',next_attempt_at=NULL WHERE id=?", recipient.getId());
        jdbc.update("UPDATE sms_confirmation_recipient SET status='SENT',next_attempt_at=NULL,sent_at=CURRENT_TIMESTAMP(6) WHERE id=?", recipient.getId());
        rejected(3819, "UPDATE sms_confirmation_recipient SET next_attempt_at=CURRENT_TIMESTAMP(6) WHERE id=?", recipient.getId());
        rejected(3819, "UPDATE sms_confirmation_recipient SET sent_at=NULL WHERE id=?", recipient.getId());
    }

    @Test void mySqlEnforcesForeignKeys() {
        var customer = customer("fk");
        var schedule = futureSchedule();
        var reservation = commands.create(customer.getId(), request(schedule.getId(), 2)).response();
        var recipient = recipient(schedule.getId(), customer.getId(), Instant.parse("2026-10-01T00:00:00Z"));
        long missing = Long.MAX_VALUE;
        rejected(1452, "INSERT INTO tour_product_style_price(tour_product_id,style,amount) VALUES (?,'GRAND',1)", missing);
        rejected(1452, "INSERT INTO tour_schedule(tour_product_id,start_date,end_date) VALUES (?,'2026-11-01','2026-11-05')", missing);
        rejected(1452, "UPDATE tour_reservation SET customer_id=? WHERE id=?", missing, reservation.id());
        rejected(1452, "UPDATE tour_reservation SET tour_schedule_id=? WHERE id=?", missing, reservation.id());
        rejected(1452, "UPDATE sms_confirmation_event SET tour_schedule_id=? WHERE id=?", missing, recipient.getConfirmationEvent().getId());
        rejected(1452, "UPDATE sms_confirmation_recipient SET confirmation_event_id=? WHERE id=?", missing, recipient.getId());
        rejected(1452, "UPDATE sms_confirmation_recipient SET customer_id=? WHERE id=?", missing, recipient.getId());
    }

    @Test void mySqlEnforcesUniqueKeys() {
        var customer = customer("unique");
        var schedule = futureSchedule();
        var recipient = recipient(schedule.getId(), customer.getId(), Instant.parse("2026-10-01T00:00:00Z"));
        rejected(1062, "INSERT INTO user_account(login_id,password_hash,name,address,contact,role) SELECT login_id,password_hash,name,address,contact,role FROM user_account WHERE id=?", customer.getId());
        rejected(1062, "INSERT INTO tour_product_style_price(tour_product_id,style,amount) VALUES (?,'GRAND',1)", schedule.getTourProduct().getId());
        rejected(1062, "INSERT INTO inventory(item_type,quantity) VALUES ('SCARF',0)");
        rejected(1062, "INSERT INTO sms_confirmation_event(tour_schedule_id,message_text,created_at) VALUES (?,'MySQL IT duplicate',CURRENT_TIMESTAMP(6))", schedule.getId());
        rejected(1062, "INSERT INTO sms_confirmation_recipient(confirmation_event_id,customer_id,contact_snapshot,status,attempt_count,next_attempt_at) VALUES (?,?,'test-only','PENDING',0,CURRENT_TIMESTAMP(6))",
                recipient.getConfirmationEvent().getId(), customer.getId());
    }

    @Test void smsTimestampsRoundTripInUtcAtMicrosecondPrecision() {
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Pacific/Honolulu"));
        var customer = customer("utc");
        var schedule = futureSchedule();
        Instant at = Instant.parse("2026-10-01T00:00:00.123456Z");
        var recipient = recipient(schedule.getId(), customer.getId(), at);
        var event = events.findById(recipient.getConfirmationEvent().getId()).orElseThrow();
        assertThat(event.getCreatedAt().truncatedTo(ChronoUnit.MICROS)).isEqualTo(at);
        assertThat(recipients.findById(recipient.getId()).orElseThrow().getNextAttemptAt()).isEqualTo(at);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var row = recipients.findByIdForDelivery(recipient.getId()).orElseThrow();
            row.markSent("test-only-provider-id", at.plusSeconds(1));
            recipients.flush();
        });
        assertThat(recipients.findById(recipient.getId()).orElseThrow().getSentAt()).isEqualTo(at.plusSeconds(1));
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var sql = connection.createStatement()) {
                try {
                    sql.execute("SET time_zone='+09:00'");
                    try (var rs = sql.executeQuery("SELECT UNIX_TIMESTAMP(created_at), CAST(created_at AS CHAR) FROM sms_confirmation_event")) {
                        rs.next();
                        assertThat(rs.getBigDecimal(1)).isEqualByComparingTo(
                                java.math.BigDecimal.valueOf(at.getEpochSecond()).add(new java.math.BigDecimal("0.123456")));
                        assertThat(rs.getString(2)).isEqualTo("2026-10-01 09:00:00.123456");
                    }
                } finally { sql.execute("SET time_zone='+00:00'"); }
            }
            return null;
        });
        assertThat(events.findById(event.getId()).orElseThrow().getCreatedAt()).isEqualTo(at);
    }

    @Test void bigintPricesAndBooleanConfirmationRoundTrip() {
        var customer = customer("bigint");
        var schedule = futureSchedule();
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isFalse();
        var result = commands.create(customer.getId(), request(schedule.getId(), 3));
        assertThat(result.response().price().unitPrice()).isEqualTo(3_000_000_001L);
        assertThat(result.response().price().total()).isEqualTo(9_000_000_003L);
        assertThat(schedules.findById(schedule.getId()).orElseThrow().isConfirmed()).isTrue();
        assertThat(jdbc.queryForObject("SELECT total FROM tour_reservation", Long.class)).isEqualTo(9_000_000_003L);
    }

    @Test void employeeBootstrapCreatesExactlyOneEmployeeAndLoginWorksOnMySql() {
        var bootstrap = new EmployeeBootstrap(accounts, passwords, validator, true,
                " B9-Test-Employee ", "b9-test-only-employee-password", "MySQL IT Employee", "Test address", "010-0000-0000");
        var args = new DefaultApplicationArguments(new String[0]);
        new TransactionTemplate(transactions).executeWithoutResult(status -> { bootstrap.run(args); bootstrap.run(args); });
        var employee = accounts.findByLoginId("b9-test-employee").orElseThrow();
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(employee.getRole()).isEqualTo(UserRole.EMPLOYEE);
        assertThat(employee.getPasswordHash()).startsWith("$2").isNotEqualTo("b9-test-only-employee-password");
        assertThat(passwords.matches("b9-test-only-employee-password", employee.getPasswordHash())).isTrue();
        var login = auth.login(new LoginRequest(" B9-TEST-EMPLOYEE ", "b9-test-only-employee-password"));
        assertThat(decoder.decode(login.accessToken()).getClaimAsString("role")).isEqualTo("EMPLOYEE");
    }

    @Test void invalidEmployeeBootstrapFailsSafelyWithoutWritingAnAccount() {
        var invalid = new EmployeeBootstrap(accounts, passwords, validator, true, "", "", "", "", "");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                invalid.run(new DefaultApplicationArguments(new String[0]))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Employee bootstrap requires valid");
        assertThat(accounts.count()).isZero();
    }

    @Test void customerSignupCanonicalDuplicateAndJwtLoginWorkOnMySql() {
        var customer = auth.signup(new SignupRequest(" B9-Test-Signup ", "b9-test-only-password",
                "MySQL IT Customer", "Test-only address", "010-0000-0000"));
        assertThat(customer.role()).isEqualTo(UserRole.CUSTOMER);
        assertThatThrownBy(() -> auth.signup(new SignupRequest("b9-test-signup", "b9-test-only-password",
                "MySQL IT Duplicate", "Test-only address", "010-0000-0000")))
                .isInstanceOf(com.wonhoone.misterworld.api.error.AuthException.class);
        var login = auth.login(new LoginRequest("B9-TEST-SIGNUP", "b9-test-only-password"));
        assertThat(decoder.decode(login.accessToken()).getSubject()).isEqualTo(Long.toString(customer.id()));
    }

    @Test void historyIndexExistsAndRepresentativeExplainIsCaptured() throws Exception {
        var customer = customer("history");
        var other = customer("other-history");
        var schedule = schedule(LocalDate.of(2026, 6, 1), true);
        historical(customer, schedule);
        // Representative selective customer predicate: 1 of 501 rows, with identical end-date ties.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            for (int i = 0; i < 500; i++) historical(other, schedule);
        });
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.statistics
                WHERE table_schema=DATABASE() AND table_name='tour_reservation'
                  AND index_name='ix_reservation_customer_end_date' ORDER BY seq_in_index
                """, String.class)).containsExactly("customer_id", "schedule_end_date_snapshot");
        jdbc.execute("ANALYZE TABLE tour_reservation");
        String query = """
                SELECT r.id, r.tour_product_id_snapshot, r.tour_product_theme_snapshot,
                  r.tour_product_name_snapshot, r.schedule_start_date_snapshot,
                  r.schedule_end_date_snapshot, r.style, r.total, r.currency
                FROM tour_reservation r JOIN tour_schedule s ON s.id=r.tour_schedule_id
                WHERE r.customer_id=? AND s.confirmed=TRUE AND r.schedule_end_date_snapshot<?
                ORDER BY r.schedule_end_date_snapshot DESC, r.id DESC
                """;
        var plan = jdbc.queryForList("EXPLAIN " + query, customer.getId(), LocalDate.of(2026, 10, 1));
        recordEvidence("History query=" + query + "; customerId=" + customer.getId() + "; businessDate=2026-10-01; fixtureRows=501; EXPLAIN=" + plan);
        assertThat(plan).isNotEmpty(); // Capture chosen plan; don't require optimizer-specific key selection.
        assertThat(reservations.findTravelHistory(customer.getId(), LocalDate.of(2026, 10, 1))).hasSize(1);
    }

    @Test void backendOnlyPublicAndAuthenticatedJourneysWorkOnMySql() throws Exception {
        var http = MockMvcBuilders.webAppContextSetup(web)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        var signup = new SignupRequest("b9-test-http", "b9-test-only-password", "MySQL IT Customer", "Test address", "010-0000-0000");
        var response = http.perform(post("/api/v1/auth/signup").contentType("application/json").content(json.writeValueAsString(signup)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long customerId = json.readTree(response).get("id").asLong();
        var login = http.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(new LoginRequest(signup.loginId(), signup.password()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + json.readTree(login).get("accessToken").asString();
        var schedule = futureSchedule();
        http.perform(get("/api/v1/tours")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("MySQL IT Product"));
        http.perform(get("/api/v1/tour-schedules")).andExpect(status().isOk()).andExpect(jsonPath("$[0].reservable").value(true));
        var created = http.perform(post("/api/v1/reservations").header("Authorization", bearer)
                .contentType("application/json").content(json.writeValueAsString(request(schedule.getId(), 3))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.schedule.recruitment.confirmed").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        http.perform(get("/api/v1/reservations/{id}", id).header("Authorization", bearer)).andExpect(status().isOk());
        historical(accounts.findById(customerId).orElseThrow(), schedule(LocalDate.of(2026, 6, 1), true));
        http.perform(get("/api/v1/customers/me/travel-history").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        var bootstrap = new EmployeeBootstrap(accounts, passwords, validator, true, "b9-test-http-employee",
                "b9-test-only-password", "MySQL IT Employee", "Test address", "010-0000-0000");
        new TransactionTemplate(transactions).executeWithoutResult(s -> bootstrap.run(new DefaultApplicationArguments(new String[0])));
        String employee = "Bearer " + auth.login(new LoginRequest("b9-test-http-employee", "b9-test-only-password")).accessToken();
        http.perform(get("/api/v1/employee/inventory").header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        http.perform(post("/api/v1/employee/inventory").header("Authorization", employee).contentType("application/json")
                .content("{\"itemType\":\"SCARF\",\"quantity\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(5));
    }

    private void assertColumn(String table, String column, String type, String nullable) {
        var row = jdbc.queryForMap("SELECT data_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", table, column);
        assertThat(row.get("data_type")).isEqualTo(type);
        assertThat(row.get("is_nullable")).isEqualTo(nullable);
    }
    private void rejected(int vendorCode, String sql, Object... args) {
        var failure = assertThrows(DataAccessException.class, () -> jdbc.update(sql, args));
        assertThat(failure.getMostSpecificCause()).isInstanceOf(SQLException.class);
        assertThat(((SQLException) failure.getMostSpecificCause()).getErrorCode()).isEqualTo(vendorCode);
    }
    static synchronized void recordEvidence(String message) throws java.io.IOException {
        System.out.println("B9_MYSQL " + message);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/b9-mysql-runtime.txt"), message + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
