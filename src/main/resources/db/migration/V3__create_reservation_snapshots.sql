CREATE TABLE tour_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    tour_schedule_id BIGINT NOT NULL,
    participant_count INTEGER NOT NULL,
    tour_product_id_snapshot BIGINT NOT NULL,
    tour_product_theme_snapshot VARCHAR(32) NOT NULL,
    tour_product_name_snapshot VARCHAR(255) NOT NULL,
    schedule_start_date_snapshot DATE NOT NULL,
    schedule_end_date_snapshot DATE NOT NULL,
    style VARCHAR(32) NOT NULL,
    hotel_option VARCHAR(32) NOT NULL,
    transport_option VARCHAR(32) NOT NULL,
    meal_option VARCHAR(32) NOT NULL,
    unit_price BIGINT NOT NULL,
    subtotal BIGINT NOT NULL,
    discount_type VARCHAR(32),
    discount_rate_percent INTEGER,
    discount_amount BIGINT,
    total BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reservation_customer FOREIGN KEY (customer_id) REFERENCES user_account (id),
    CONSTRAINT fk_reservation_schedule FOREIGN KEY (tour_schedule_id) REFERENCES tour_schedule (id),
    CONSTRAINT ck_reservation_party CHECK (participant_count BETWEEN 1 AND 10),
    CONSTRAINT ck_reservation_product_identity CHECK (tour_product_id_snapshot > 0),
    CONSTRAINT ck_reservation_theme CHECK (tour_product_theme_snapshot IN
        ('HONEYMOON_ROMANCE', 'PARENTS_HEALING', 'GOLF_CHALLENGE', 'OUTDOOR_TREKKING')),
    CONSTRAINT ck_reservation_dates CHECK (schedule_start_date_snapshot <= schedule_end_date_snapshot),
    CONSTRAINT ck_reservation_style CHECK (style IN ('CLASSIC', 'GRAND', 'PREMIUM')),
    CONSTRAINT ck_reservation_hotel CHECK (hotel_option IN ('HOTEL_3_STAR', 'HOTEL_4_STAR', 'HOTEL_5_STAR')),
    CONSTRAINT ck_reservation_transport CHECK (transport_option IN ('PRIVATE_LUXURY_CAR_2', 'PREMIUM_VAN_10')),
    CONSTRAINT ck_reservation_meal CHECK (meal_option IN ('LUNCH_BOX', 'LOCAL_RESTAURANT', 'PREMIUM_RESTAURANT')),
    CONSTRAINT ck_reservation_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_reservation_subtotal CHECK (subtotal > 0),
    CONSTRAINT ck_reservation_total CHECK (total >= 0),
    CONSTRAINT ck_reservation_currency CHECK (currency = 'KRW'),
    CONSTRAINT ck_reservation_discount CHECK (
        (discount_type IS NULL AND discount_rate_percent IS NULL AND discount_amount IS NULL AND total = subtotal)
        OR
        (discount_type IS NOT NULL AND discount_type = 'LOYALTY'
         AND discount_rate_percent IS NOT NULL AND discount_rate_percent = 5
         AND discount_amount IS NOT NULL AND discount_amount >= 0 AND discount_amount <= subtotal
         AND total = subtotal - discount_amount)
    )
);

CREATE INDEX ix_reservation_customer_end_date ON tour_reservation (customer_id, schedule_end_date_snapshot);
CREATE INDEX ix_reservation_schedule ON tour_reservation (tour_schedule_id);

CREATE TABLE tour_reservation_extra_option (
    reservation_id BIGINT NOT NULL,
    extra_option VARCHAR(32) NOT NULL,
    PRIMARY KEY (reservation_id, extra_option),
    CONSTRAINT fk_reservation_extra_owner FOREIGN KEY (reservation_id) REFERENCES tour_reservation (id) ON DELETE CASCADE,
    CONSTRAINT ck_reservation_extra CHECK (extra_option IN ('CHAMPAGNE', 'COFFEE'))
);
