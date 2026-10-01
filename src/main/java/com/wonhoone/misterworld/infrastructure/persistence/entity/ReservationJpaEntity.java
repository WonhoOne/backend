package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.*;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tour_reservation")
public class ReservationJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private UserAccountJpaEntity customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_schedule_id", nullable = false, updatable = false)
    private TourScheduleJpaEntity tourSchedule;

    @Column(name = "participant_count", nullable = false, updatable = false)
    private int participantCount;
    @Column(name = "tour_product_id_snapshot", nullable = false, updatable = false)
    private long tourProductIdSnapshot;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "tour_product_theme_snapshot", nullable = false, length = 32, updatable = false)
    private Theme tourProductThemeSnapshot;
    @Column(name = "tour_product_name_snapshot", nullable = false, length = 255, updatable = false)
    private String tourProductNameSnapshot;
    @Column(name = "schedule_start_date_snapshot", nullable = false, updatable = false)
    private LocalDate scheduleStartDateSnapshot;
    @Column(name = "schedule_end_date_snapshot", nullable = false, updatable = false)
    private LocalDate scheduleEndDateSnapshot;

    @Embedded
    private ReservationConfigurationEmbeddable configuration;
    @Embedded
    private ReservationPriceSnapshotEmbeddable price;

    protected ReservationJpaEntity() {}

    /** Copy historical values now: later catalog edits must never rewrite a customer's trip. */
    public static ReservationJpaEntity capture(UserAccountJpaEntity customer, TourScheduleJpaEntity schedule,
                                                int participantCount, TourConfiguration configuration,
                                                ReservationPriceSnapshot price) {
        Objects.requireNonNull(customer, "customer must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");
        Objects.requireNonNull(price, "price must not be null");
        var product = schedule.getTourProduct();
        Objects.requireNonNull(product.getId(), "product must already be persisted");
        ReservationConfigurationPolicy.validate(product.getTheme(), participantCount, configuration);
        if (price.subtotal() != Math.multiplyExact(price.unitPrice(), participantCount)) {
            throw new IllegalArgumentException("Price subtotal must match the reservation participant count");
        }
        var reservation = new ReservationJpaEntity();
        reservation.customer = customer;
        reservation.tourSchedule = schedule;
        reservation.participantCount = participantCount;
        reservation.tourProductIdSnapshot = product.getId();
        reservation.tourProductThemeSnapshot = product.getTheme();
        reservation.tourProductNameSnapshot = product.getName();
        reservation.scheduleStartDateSnapshot = schedule.getStartDate();
        reservation.scheduleEndDateSnapshot = schedule.getEndDate();
        reservation.configuration = ReservationConfigurationEmbeddable.capture(configuration);
        reservation.price = ReservationPriceSnapshotEmbeddable.capture(price);
        return reservation;
    }

    public Long getId() { return id; }
    public UserAccountJpaEntity getCustomer() { return customer; }
    public TourScheduleJpaEntity getTourSchedule() { return tourSchedule; }
    public int getParticipantCount() { return participantCount; }
    public long getTourProductIdSnapshot() { return tourProductIdSnapshot; }
    public Theme getTourProductThemeSnapshot() { return tourProductThemeSnapshot; }
    public String getTourProductNameSnapshot() { return tourProductNameSnapshot; }
    public LocalDate getScheduleStartDateSnapshot() { return scheduleStartDateSnapshot; }
    public LocalDate getScheduleEndDateSnapshot() { return scheduleEndDateSnapshot; }
    public TourConfiguration getConfiguration() { return configuration.toDomain(); }
    public ReservationPriceSnapshot getPrice() { return price.toDomain(); }
}
