-- Fixed stock aggregates exist even before any Employee adds stock.
INSERT INTO inventory (item_type, quantity) VALUES
    ('COUPLE_TSHIRT', 0),
    ('GINSENG_GIFT', 0),
    ('GOLF_BALL', 0),
    ('SCARF', 0);
