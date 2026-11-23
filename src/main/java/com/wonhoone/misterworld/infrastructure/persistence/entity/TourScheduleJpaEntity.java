package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.ScheduleOperationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tour_schedule")
public class TourScheduleJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_product_id", nullable = false)
    private TourProductJpaEntity tourProduct;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "operation_status", nullable = false, length = 16)
    private ScheduleOperationStatus operationStatus = ScheduleOperationStatus.SCHEDULED;

    protected TourScheduleJpaEntity() {
    }

    public TourScheduleJpaEntity(TourProductJpaEntity tourProduct, LocalDate startDate,
                                 LocalDate endDate, boolean confirmed) {
        this.tourProduct = Objects.requireNonNull(tourProduct, "tourProduct must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }
        this.confirmed = confirmed;
    }

    public Long getId() {
        return id;
    }

    public TourProductJpaEntity getTourProduct() {
        return tourProduct;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    /** The caller checks the recruitment threshold while holding this Schedule's write lock. */
    public boolean markConfirmed() {
        if (confirmed) return false;
        confirmed = true;
        return true;
    }

    public ScheduleOperationStatus getOperationStatus() {
        return operationStatus;
    }

    /** Only a recruitment-confirmed schedule that has not departed can depart. Caller holds the row lock. */
    public void markDeparted() {
        if (!confirmed) throw new IllegalStateException("Only a confirmed schedule can depart");
        if (operationStatus != ScheduleOperationStatus.SCHEDULED) {
            throw new IllegalStateException("Schedule has already departed");
        }
        operationStatus = ScheduleOperationStatus.DEPARTED;
    }

    public void markCompleted() {
        if (operationStatus != ScheduleOperationStatus.DEPARTED) {
            throw new IllegalStateException("Only a departed schedule can be completed");
        }
        operationStatus = ScheduleOperationStatus.COMPLETED;
    }
}
