CREATE TABLE user_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NOT NULL,
    contact VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_account_login_id UNIQUE (login_id),
    CONSTRAINT ck_user_account_role CHECK (role IN ('CUSTOMER', 'EMPLOYEE'))
);

CREATE TABLE tour_product (
    id BIGINT NOT NULL AUTO_INCREMENT,
    theme VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_tour_product_theme CHECK (
        theme IN ('HONEYMOON_ROMANCE', 'PARENTS_HEALING', 'GOLF_CHALLENGE', 'OUTDOOR_TREKKING')
    )
);

CREATE TABLE tour_product_style_price (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tour_product_id BIGINT NOT NULL,
    style VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_style_price_tour_product FOREIGN KEY (tour_product_id) REFERENCES tour_product (id),
    CONSTRAINT uk_style_price_product_style UNIQUE (tour_product_id, style),
    CONSTRAINT ck_style_price_style CHECK (style IN ('CLASSIC', 'GRAND', 'PREMIUM')),
    CONSTRAINT ck_style_price_positive_amount CHECK (amount > 0)
);

CREATE TABLE tour_schedule (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tour_product_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_tour_product FOREIGN KEY (tour_product_id) REFERENCES tour_product (id),
    CONSTRAINT ck_schedule_date_order CHECK (start_date <= end_date)
);

CREATE TABLE inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_type VARCHAR(32) NOT NULL,
    quantity BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_item_type UNIQUE (item_type),
    CONSTRAINT ck_inventory_item_type CHECK (
        item_type IN ('COUPLE_TSHIRT', 'GINSENG_GIFT', 'GOLF_BALL', 'SCARF')
    ),
    CONSTRAINT ck_inventory_nonnegative_quantity CHECK (quantity >= 0)
);
