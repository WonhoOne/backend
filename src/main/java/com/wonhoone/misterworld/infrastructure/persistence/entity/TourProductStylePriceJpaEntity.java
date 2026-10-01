package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.TourStyle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tour_product_style_price",
        uniqueConstraints = @UniqueConstraint(name = "uk_style_price_product_style",
                columnNames = {"tour_product_id", "style"}))
public class TourProductStylePriceJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_product_id", nullable = false)
    private TourProductJpaEntity tourProduct;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "style", nullable = false, length = 32)
    private TourStyle style;

    @Column(name = "amount", nullable = false)
    private long amount;

    protected TourProductStylePriceJpaEntity() {
    }

    public TourProductStylePriceJpaEntity(TourProductJpaEntity tourProduct, TourStyle style, long amount) {
        this.tourProduct = Objects.requireNonNull(tourProduct, "tourProduct must not be null");
        this.style = Objects.requireNonNull(style, "style must not be null");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive integer KRW");
        }
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public TourProductJpaEntity getTourProduct() {
        return tourProduct;
    }

    public TourStyle getStyle() {
        return style;
    }

    public long getAmount() {
        return amount;
    }
}
