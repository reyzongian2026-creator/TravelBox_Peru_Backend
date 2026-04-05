-- Add missing audit columns (created_by, updated_by) required by AuditableEntity
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE promo_codes ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'system';

ALTER TABLE referral_codes ADD COLUMN IF NOT EXISTS created_by VARCHAR(255) NOT NULL DEFAULT 'system';
ALTER TABLE referral_codes ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255) NOT NULL DEFAULT 'system';
