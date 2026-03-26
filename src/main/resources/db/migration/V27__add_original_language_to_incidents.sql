ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS original_language VARCHAR(5) NOT NULL DEFAULT 'es';

COMMENT ON COLUMN incidents.original_language IS 'Original language used when the incident was reported';
