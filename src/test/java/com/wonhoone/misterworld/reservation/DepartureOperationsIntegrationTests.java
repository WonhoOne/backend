package com.wonhoone.misterworld.reservation;

import com.wonhoone.misterworld.domain.*;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DepartureOperationsIntegrationTests extends ReservationIntegrationSupport {
    private static final String PATH = "/api/v1/employee/tour-schedules";
    private String employee;

    @BeforeEach void prepareEmployee() {
        jdbc.update("UPDATE inventory SET quantity = 0");
        employee = token(9_000L, UserRole.EMPLOYEE);
    }

    @AfterEach void resetInventory() {
        jdbc.update("UPDATE inventory SET quantity = 0");
    }

    private void stock(InventoryItemType type, long quantity) {
        jdbc.update("UPDATE inventory SET quantity = ? WHERE item_type = ?", quantity, type.name());
    }

    private long stock(InventoryItemType type) {
        return jdbc.queryForObject("select quantity from inventory where item_type = ?", Long.class, type.name());
    }

    private String operationStatus(long scheduleId) {
        return jdbc.queryForObject("select operation_status from tour_schedule where id = ?", String.class, scheduleId);
    }

    private org.springframework.test.web.servlet.ResultActions depart(long scheduleId, String authorization) throws Exception {
        return http.perform(post(PATH + "/{id}/departure", scheduleId).header("Authorization", authorization));
    }

    private org.springframework.test.web.servlet.ResultActions complete(long scheduleId) throws Exception {
        return http.perform(post(PATH + "/{id}/completion", scheduleId).header("Authorization", employee));
    }

    @Test void confirmedTrekkingDepartsDeductsScarvesThenCompletesAndAppearsInMyTrips() throws Exception {
        var trekking = schedule(Theme.OUTDOOR_TREKKING, LocalDate.of(2026, 12, 19), false);
        postId(request(trekking, 3));
        stock(InventoryItemType.SCARF, 5);

        http.perform(get(PATH).header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + trekking.getId() + ")].participantCount").value(3))
                .andExpect(jsonPath("$[?(@.id == " + trekking.getId() + ")].confirmed").value(true))
                .andExpect(jsonPath("$[?(@.id == " + trekking.getId() + ")].operationStatus").value("SCHEDULED"));

        depart(trekking.getId(), employee).andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.operationStatus").value("DEPARTED"))
                .andExpect(jsonPath("$.schedule.theme").value("OUTDOOR_TREKKING"))
                .andExpect(jsonPath("$.deductedItems[0].itemType").value("SCARF"))
                .andExpect(jsonPath("$.deductedItems[0].quantity").value(3))
                .andExpect(jsonPath("$.deductedItems[0].remainingQuantity").value(2));
        assertThat(stock(InventoryItemType.SCARF)).isEqualTo(2);

        // Departed schedules close intake.
        http.perform(get("/api/v1/tour-schedules/{id}", trekking.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reservable").value(false));
        postReservation(request(trekking, 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_RESERVABLE"));

        depart(trekking.getId(), employee).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_ALREADY_DEPARTED"));
        assertThat(stock(InventoryItemType.SCARF)).isEqualTo(2);

        complete(trekking.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.operationStatus").value("COMPLETED"));
        http.perform(get("/api/v1/customers/me/travel-history").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tourProduct.theme").value("OUTDOOR_TREKKING"));
    }

    @Test void honeymoonDeductsOneCoupleShirtPerCouple() throws Exception {
        var honeymoon = schedule(Theme.HONEYMOON_ROMANCE, LocalDate.of(2026, 12, 24), false);
        postId(request(honeymoon, 4));
        stock(InventoryItemType.COUPLE_TSHIRT, 10);
        depart(honeymoon.getId(), employee).andExpect(status().isOk())
                .andExpect(jsonPath("$.deductedItems[0].itemType").value("COUPLE_TSHIRT"))
                .andExpect(jsonPath("$.deductedItems[0].quantity").value(2))
                .andExpect(jsonPath("$.deductedItems[0].remainingQuantity").value(8));
    }

    @Test void insufficientStockChangesNothing() throws Exception {
        var golf = schedule(Theme.GOLF_CHALLENGE, LocalDate.of(2026, 12, 12), false);
        postId(request(golf, 3));
        stock(InventoryItemType.GOLF_BALL, 2);
        depart(golf.getId(), employee).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_INVENTORY"));
        assertThat(stock(InventoryItemType.GOLF_BALL)).isEqualTo(2);
        assertThat(operationStatus(golf.getId())).isEqualTo("SCHEDULED");
    }

    @Test void stateRulesAndAuthorization() throws Exception {
        var unconfirmed = schedule(Theme.PARENTS_HEALING, LocalDate.of(2026, 12, 5), false);
        postId(request(unconfirmed, 2));
        depart(unconfirmed.getId(), employee).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_CONFIRMED"));
        complete(unconfirmed.getId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_DEPARTED"));
        depart(999_999L, employee).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOUR_SCHEDULE_NOT_FOUND"));
        depart(unconfirmed.getId(), bearer).andExpect(status().isForbidden());
        http.perform(get(PATH).header("Authorization", bearer)).andExpect(status().isForbidden());
        assertThat(operationStatus(unconfirmed.getId())).isEqualTo("SCHEDULED");
    }
}
