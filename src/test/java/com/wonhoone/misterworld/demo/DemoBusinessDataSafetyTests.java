package com.wonhoone.misterworld.demo;

import com.wonhoone.misterworld.application.demo.*;
import com.wonhoone.misterworld.application.tour.TourProductCommandService;
import com.wonhoone.misterworld.infrastructure.persistence.repository.*;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DemoBusinessDataSafetyTests {
    @ParameterizedTest @ValueSource(strings = {"product", "schedule", "reservation", "event", "recipient"})
    void eachExistingBusinessTableIndependentlyBlocksAllWrites(String existing) {
        var validator = mock(DemoScenarioValidator.class); var jdbc = mock(JdbcTemplate.class);
        var commands = mock(TourProductCommandService.class); var products = mock(TourProductJpaRepository.class);
        var schedules = mock(TourScheduleJpaRepository.class); var reservations = mock(ReservationJpaRepository.class);
        var events = mock(SmsConfirmationEventJpaRepository.class); var recipients = mock(SmsConfirmationRecipientJpaRepository.class);
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("test-only");
        switch (existing) {
            case "product" -> when(products.count()).thenReturn(1L);
            case "schedule" -> when(schedules.count()).thenReturn(1L);
            case "reservation" -> when(reservations.count()).thenReturn(1L);
            case "event" -> when(events.count()).thenReturn(1L);
            case "recipient" -> when(recipients.count()).thenReturn(1L);
            default -> throw new IllegalStateException();
        }
        var service = new DemoScenarioProvisioner(validator, jdbc, commands, products, schedules, reservations, events, recipients);
        assertThatThrownBy(() -> service.provision(new DemoScenarioManifest(1, List.of(), List.of()), "test-only"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("empty");
        verifyNoInteractions(commands); verify(products, never()).save(any()); verify(schedules, never()).save(any());
    }
}
