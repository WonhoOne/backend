package com.wonhoone.misterworld.api.dto;

import com.wonhoone.misterworld.domain.InventoryItemType;
import com.wonhoone.misterworld.domain.ScheduleOperationStatus;
import com.wonhoone.misterworld.domain.Theme;
import java.time.LocalDate;
import java.util.List;

/** Employee departure-operations view of a schedule. Contains no customer data. */
public record EmployeeTourScheduleResponse(long id, long tourId, String tourName, Theme theme,
                                           LocalDate startDate, LocalDate endDate, long participantCount,
                                           boolean confirmed, ScheduleOperationStatus operationStatus) {
    public record Departure(EmployeeTourScheduleResponse schedule, List<DeductedItem> deductedItems) {}
    public record DeductedItem(InventoryItemType itemType, long quantity, long remainingQuantity) {}
    public record Completion(EmployeeTourScheduleResponse schedule) {}
}
