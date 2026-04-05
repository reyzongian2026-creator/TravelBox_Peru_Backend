-- Favorites: user-warehouse favorites
CREATE TABLE IF NOT EXISTS favorite_warehouses (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    warehouse_id    BIGINT      NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, warehouse_id)
);

CREATE INDEX idx_favorite_warehouses_user ON favorite_warehouses(user_id);
