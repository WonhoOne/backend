package com.wonhoone.misterworld.application.tour;

import com.wonhoone.misterworld.api.dto.EmployeeTourScheduleResponse;
import com.wonhoone.misterworld.api.error.InvalidRequestParameterException;
import com.wonhoone.misterworld.api.error.ResourceConflictException;
import com.wonhoone.misterworld.api.error.ResourceNotFoundException;
import com.wonhoone.misterworld.domain.DepartureSouvenirPolicy;
import com.wonhoone.misterworld.domain.ScheduleOperationStatus;
import com.wonhoone.misterworld.infrastructure.persistence.entity.TourScheduleJpaEntity;
import com.wonhoone.misterworld.infrastructure.persistence.repository.InventoryJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.ReservationJpaRepository;
import com.wonhoone.misterworld.infrastructure.persistence.repository.TourScheduleJpaRepository;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Employee departure operations: depart a confirmed schedule (souvenirs leave stock), then complete it. */
@Service
public class TourScheduleOperationService {
    private final TourScheduleJpaRepository schedules;
    private final ReservationJpaRepository reservations;
    private final InventoryJpaRepository inventory;

    public TourScheduleOperationService(TourScheduleJpaRepository schedules, ReservationJpaRepository reservations,
                                        InventoryJpaRepository inventory) {
        this.schedules = schedules;
        this.reservations = reservations;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public List<EmployeeTourScheduleResponse> list() {
        var result = schedules.findAllByOrderByStartDateAscIdAsc();
        if (result.isEmpty()) return List.of();
        var ids = result.stream().map(TourScheduleJpaEntity::getId).toList();
        var totals = reservations.totalParticipantsByScheduleIds(ids).stream()
                .collect(Collectors.toMap(ReservationJpaRepository.ScheduleParticipantTotal::getScheduleId,
                        ReservationJpaRepository.ScheduleParticipantTotal::getParticipantTotal));
        return result.stream().map(schedule -> project(schedule, totals.getOrDefault(schedule.getId(), 0L))).toList();
    }

    // READ_COMMITTED + the schedule row lock serialize departure with reservation create/modify on the same schedule.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeTourScheduleResponse.Departure depart(long scheduleId) {
        var schedule = lock(scheduleId);
        if (!schedule.isConfirmed()) {
            throw new ResourceConflictException("SCHEDULE_NOT_CONFIRMED",
                    "Only a schedule whose recruitment is confirmed can depart.");
        }
        if (schedule.getOperationStatus() != ScheduleOperationStatus.SCHEDULED) {
            throw new ResourceConflictException("SCHEDULE_ALREADY_DEPARTED", "This schedule has already departed.");
        }
        var theme = schedule.getTourProduct().getTheme();
        long participants = reservations.totalParticipantsForSchedule(scheduleId);
        long quantity = DepartureSouvenirPolicy.quantity(theme, participants);
        var deducted = List.<EmployeeTourScheduleResponse.DeductedItem>of();
        if (quantity > 0) {
            var itemType = DepartureSouvenirPolicy.itemType(theme);
            var item = inventory.findByItemTypeForUpdate(itemType)
                    .orElseThrow(() -> new IllegalStateException("Inventory catalog row is missing"));
            if (item.getQuantity() < quantity) {
                // Nothing changes: the transaction rolls back and the schedule stays SCHEDULED.
                throw new ResourceConflictException("INSUFFICIENT_INVENTORY",
                        "Not enough " + itemType + " in stock: " + quantity + " needed, " + item.getQuantity() + " available.");
            }
            item.removeQuantity(quantity);
            inventory.flush();
            deducted = List.of(new EmployeeTourScheduleResponse.DeductedItem(itemType, quantity, item.getQuantity()));
        }
        schedule.markDeparted();
        schedules.flush();
        return new EmployeeTourScheduleResponse.Departure(project(schedule, participants), deducted);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeTourScheduleResponse.Completion complete(long scheduleId) {
        var schedule = lock(scheduleId);
        if (schedule.getOperationStatus() != ScheduleOperationStatus.DEPARTED) {
            throw new ResourceConflictException("SCHEDULE_NOT_DEPARTED", "Only a departed schedule can be completed.");
        }
        schedule.markCompleted();
        schedules.flush();
        return new EmployeeTourScheduleResponse.Completion(
                project(schedule, reservations.totalParticipantsForSchedule(scheduleId)));
    }

    private TourScheduleJpaEntity lock(long scheduleId) {
        InvalidRequestParameterException.requirePositive(scheduleId);
        return schedules.findByIdForReservationUpdate(scheduleId).orElseThrow(() ->
                new ResourceNotFoundException("TOUR_SCHEDULE_NOT_FOUND", "Tour schedule was not found."));
    }

    private static EmployeeTourScheduleResponse project(TourScheduleJpaEntity schedule, long participants) {
        var product = schedule.getTourProduct();
        return new EmployeeTourScheduleResponse(schedule.getId(), product.getId(), product.getName(), product.getTheme(),
                schedule.getStartDate(), schedule.getEndDate(), participants, schedule.isConfirmed(),
                schedule.getOperationStatus());
    }
}
