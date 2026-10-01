package com.wonhoone.misterworld.infrastructure.persistence.entity;

import jakarta.persistence.Column;
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
}
