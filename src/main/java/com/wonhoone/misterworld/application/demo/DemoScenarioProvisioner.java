package com.wonhoone.misterworld.application.demo;

import com.wonhoone.misterworld.application.tour.TourProductCommandService;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoScenarioProvisioner {
    private static final Logger log = LoggerFactory.getLogger(DemoScenarioProvisioner.class);
    private final DemoScenarioValidator validator;
    private final JdbcTemplate jdbc;
    private final TourProductCommandService commands;
    private final TourProductJpaRepository products;
    private final TourScheduleJpaRepository schedules;
    private final ReservationJpaRepository reservations;
    private final SmsConfirmationEventJpaRepository events;
    private final SmsConfirmationRecipientJpaRepository recipients;

    public DemoScenarioProvisioner(DemoScenarioValidator validator, JdbcTemplate jdbc,
            TourProductCommandService commands, TourProductJpaRepository products,
            TourScheduleJpaRepository schedules, ReservationJpaRepository reservations,
            SmsConfirmationEventJpaRepository events, SmsConfirmationRecipientJpaRepository recipients) {
        this.validator = validator; this.jdbc = jdbc; this.commands = commands; this.products = products;
        this.schedules = schedules; this.reservations = reservations; this.events = events; this.recipients = recipients;
    }

    /** One-shot setup. Run with exclusive access to a disposable DB, never alongside another app. */
    @Transactional
    public void provision(DemoScenarioManifest manifest, String expectedDatabase) {
        validator.validate(manifest); // Validate every item before any DB write.
        if (expectedDatabase == null || expectedDatabase.isBlank()
                || !expectedDatabase.equals(jdbc.queryForObject("SELECT DATABASE()", String.class)))
            throw new IllegalStateException("Demo database identity does not match DEMO_EXPECTED_DATABASE");
        if (products.count() != 0 || schedules.count() != 0 || reservations.count() != 0
                || events.count() != 0 || recipients.count() != 0)
            throw new IllegalStateException("Demo provisioning requires empty product, schedule, reservation and SMS tables");
        var ids = new LinkedHashMap<String, Long>();
        for (var product : manifest.products()) ids.put(product.key(), commands.create(product.request()).id());
        products.flush();
        for (var schedule : manifest.schedules()) {
            schedules.save(new TourScheduleJpaEntity(products.getReferenceById(ids.get(schedule.productKey())),
                    schedule.startDate(), schedule.endDate(), false));
        }
        schedules.flush();
        log.info("Demo scenario rows prepared schemaVersion={} products={} schedules={}",
                manifest.schemaVersion(), ids.size(), manifest.schedules().size());
    }
}
