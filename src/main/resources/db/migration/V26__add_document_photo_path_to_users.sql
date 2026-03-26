ALTER TABLE users
    ADD COLUMN IF NOT EXISTS document_photo_path VARCHAR(260);

COMMENT ON COLUMN users.document_photo_path IS 'Remote URL for worker document image uploaded by admin';
