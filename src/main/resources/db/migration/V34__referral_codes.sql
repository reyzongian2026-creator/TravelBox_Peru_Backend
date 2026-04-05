-- Referral codes system
CREATE TABLE referral_codes (
    id               BIGSERIAL PRIMARY KEY,
    owner_user_id    BIGINT         NOT NULL REFERENCES users(id),
    code             VARCHAR(20)    NOT NULL UNIQUE,
    reward_amount    NUMERIC(12,2)  NOT NULL DEFAULT 5.00,
    max_uses         INT            DEFAULT 50,
    current_uses     INT            NOT NULL DEFAULT 0,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_referral_codes_owner ON referral_codes (owner_user_id);
CREATE INDEX idx_referral_codes_code  ON referral_codes (code);

-- Track referral redemptions
CREATE TABLE referral_redemptions (
    id                BIGSERIAL PRIMARY KEY,
    referral_code_id  BIGINT    NOT NULL REFERENCES referral_codes(id),
    referred_user_id  BIGINT    NOT NULL REFERENCES users(id),
    redeemed_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (referral_code_id, referred_user_id)
);

-- Add wallet_balance to users for referral credits
ALTER TABLE users ADD COLUMN IF NOT EXISTS wallet_balance NUMERIC(12,2) NOT NULL DEFAULT 0;
