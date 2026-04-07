-- Platform settings table for admin-configurable key-value pairs
CREATE TABLE IF NOT EXISTS platform_settings (
    setting_key   VARCHAR(128)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(2000),
    updated_at    TIMESTAMPTZ   DEFAULT now()
);

-- Seed default payment method settings
INSERT INTO platform_settings (setting_key, setting_value) VALUES
    ('payments.yape.phone', ''),
    ('payments.yape.name', 'Reyzon Gianfranco Lopez Huaman'),
    ('payments.yape.qr_url', ''),
    ('payments.plin.phone', ''),
    ('payments.plin.name', 'Reyzon Gianfranco Lopez Huaman'),
    ('payments.plin.qr_url', ''),
    ('payments.qr.phone', ''),
    ('payments.qr.name', 'Reyzon Gianfranco Lopez Huaman'),
    ('payments.qr.qr_url', '')
ON CONFLICT (setting_key) DO NOTHING;
