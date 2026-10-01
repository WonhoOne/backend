package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.*;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class ReservationConfigurationEmbeddable {
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "style", nullable = false, length = 32, updatable = false)
    private TourStyle style;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "hotel_option", nullable = false, length = 32, updatable = false)
    private HotelOption hotelOption;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "transport_option", nullable = false, length = 32, updatable = false)
    private TransportOption transportOption;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "meal_option", nullable = false, length = 32, updatable = false)
    private MealOption mealOption;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tour_reservation_extra_option",
            joinColumns = @JoinColumn(name = "reservation_id", nullable = false))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "extra_option", nullable = false, length = 32)
    private Set<ExtraOption> extraOptions = new HashSet<>();

    protected ReservationConfigurationEmbeddable() {}

    static ReservationConfigurationEmbeddable capture(TourConfiguration configuration) {
        var snapshot = new ReservationConfigurationEmbeddable();
        snapshot.style = configuration.style();
        snapshot.hotelOption = configuration.hotelOption();
        snapshot.transportOption = configuration.transportOption();
        snapshot.mealOption = configuration.mealOption();
        snapshot.extraOptions = new HashSet<>(configuration.extraOptions());
        return snapshot;
    }

    public TourConfiguration toDomain() {
        return new TourConfiguration(style, hotelOption, transportOption, mealOption, extraOptions);
    }
}
