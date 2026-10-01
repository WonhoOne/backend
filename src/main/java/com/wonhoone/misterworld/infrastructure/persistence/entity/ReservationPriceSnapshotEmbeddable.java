package com.wonhoone.misterworld.infrastructure.persistence.entity;

import com.wonhoone.misterworld.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class ReservationPriceSnapshotEmbeddable {
    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;
    @Column(name = "subtotal", nullable = false, updatable = false)
    private long subtotal;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "discount_type", length = 32, updatable = false)
    private DiscountType discountType;
    @Column(name = "discount_rate_percent", updatable = false)
    private Integer discountRatePercent;
    @Column(name = "discount_amount", updatable = false)
    private Long discountAmount;
    @Column(name = "total", nullable = false, updatable = false)
    private long total;
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    protected ReservationPriceSnapshotEmbeddable() {}

    static ReservationPriceSnapshotEmbeddable capture(ReservationPriceSnapshot price) {
        var snapshot = new ReservationPriceSnapshotEmbeddable();
        snapshot.unitPrice = price.unitPrice();
        snapshot.subtotal = price.subtotal();
        snapshot.total = price.total();
        snapshot.currency = price.currency();
        if (price.discount() != null) {
            snapshot.discountType = price.discount().type();
            snapshot.discountRatePercent = price.discount().ratePercent();
            snapshot.discountAmount = price.discount().amount();
        }
        return snapshot;
    }

    public ReservationPriceSnapshot toDomain() {
        var discount = discountType == null ? null
                : new DiscountSnapshot(discountType, discountRatePercent, discountAmount);
        return new ReservationPriceSnapshot(unitPrice, subtotal, discount, total, currency);
    }
}
