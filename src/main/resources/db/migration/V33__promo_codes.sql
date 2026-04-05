-- Promo codes system
CREATE TABLE promo_codes (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30)    NOT NULL UNIQUE,
    description     VARCHAR(200),
    discount_type   VARCHAR(20)    NOT NULL DEFAULT 'PERCENTAGE',  -- PERCENTAGE | FIXED_AMOUNT
    discount_value  NUMERIC(12,2)  NOT NULL,
    min_order_amount NUMERIC(12,2) DEFAULT 0,
    max_discount    NUMERIC(12,2),
    max_uses        INT,
    current_uses    INT            NOT NULL DEFAULT 0,
    valid_from      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    valid_until     TIMESTAMPTZ,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promo_codes_code ON promo_codes (code);

-- Track discount applied on each payment
ALTER TABLE payment_attempts ADD COLUMN promo_code_id    BIGINT REFERENCES promo_codes(id);
ALTER TABLE payment_attempts ADD COLUMN discount_amount  NUMERIC(12,2) DEFAULT 0;
