-- V24: Add encrypted versions of sensitive user fields (AES-256-GCM)
-- These columns store encrypted versions of sensitive personal data

ALTER TABLE users ADD COLUMN phone_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN address_line_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN primary_document_number_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN secondary_document_number_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN emergency_contact_name_encrypted VARCHAR(500);
ALTER TABLE users ADD COLUMN emergency_contact_phone_encrypted VARCHAR(500);

-- Indexes for encrypted columns (for potential search scenarios)
CREATE INDEX idx_users_phone_encrypted ON users(phone_encrypted) WHERE phone_encrypted IS NOT NULL;
CREATE INDEX idx_users_primary_doc_encrypted ON users(primary_document_number_encrypted) WHERE primary_document_number_encrypted IS NOT NULL;

-- Comments for documentation
COMMENT ON COLUMN users.phone_encrypted IS 'AES-256-GCM encrypted phone number';
COMMENT ON COLUMN users.address_line_encrypted IS 'AES-256-GCM encrypted address line';
COMMENT ON COLUMN users.primary_document_number_encrypted IS 'AES-256-GCM encrypted primary document number (DNI/RUC/Passport)';
COMMENT ON COLUMN users.secondary_document_number_encrypted IS 'AES-256-GCM encrypted secondary document number';
COMMENT ON COLUMN users.emergency_contact_name_encrypted IS 'AES-256-GCM encrypted emergency contact name';
COMMENT ON COLUMN users.emergency_contact_phone_encrypted IS 'AES-256-GCM encrypted emergency contact phone';
