-- V25: Add audit_log and generated_reports tables for compliance tracking

-- Audit log table for persistent storage
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50),
    details TEXT,
    performed_by VARCHAR(100),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    performed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_performed_by ON audit_log(performed_by);
CREATE INDEX idx_audit_performed_at ON audit_log(performed_at);

COMMENT ON TABLE audit_log IS 'Persistent audit trail for compliance and security tracking';
COMMENT ON COLUMN audit_log.entity_type IS 'USER, RESERVATION, INVENTORY, WAREHOUSE, PAYMENT, REPORT, FILE_UPLOAD, FILE_DELETE, etc.';
COMMENT ON COLUMN audit_log.ip_address IS 'IPv4 or IPv6 address of the client';
COMMENT ON COLUMN audit_log.user_agent IS 'Browser/client user agent string';

-- Generated reports table for tracking PDF/Excel generation
CREATE TABLE generated_reports (
    id BIGSERIAL PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    format VARCHAR(20) NOT NULL,
    parameters TEXT,
    file_path VARCHAR(500),
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    generated_by VARCHAR(100),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    access_count INTEGER NOT NULL DEFAULT 0,
    last_accessed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_reports_type ON generated_reports(report_type);
CREATE INDEX idx_reports_format ON generated_reports(format);
CREATE INDEX idx_reports_generated_by ON generated_reports(generated_by);
CREATE INDEX idx_reports_expires_at ON generated_reports(expires_at) WHERE expires_at IS NOT NULL;

COMMENT ON TABLE generated_reports IS 'Track all generated PDF/Excel reports for compliance and cleanup';
COMMENT ON COLUMN generated_reports.report_type IS 'Type of report: RESERVATIONS, INVENTORY, FINANCIAL, etc.';
COMMENT ON COLUMN generated_reports.format IS 'PDF or XLSX';
COMMENT ON COLUMN generated_reports.parameters IS 'JSON string with report parameters/filters used';

-- Add storage_container column to stored_item_evidences for Azure tracking
ALTER TABLE stored_item_evidences
    ADD COLUMN storage_container VARCHAR(100);

COMMENT ON COLUMN stored_item_evidences.storage_container IS 'Azure Blob container name where evidence is stored';