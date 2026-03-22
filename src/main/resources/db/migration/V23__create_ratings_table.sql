-- V23: Create ratings table for warehouse/service reviews
CREATE TABLE ratings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'system',
    updated_by VARCHAR(255) NOT NULL DEFAULT 'system',
    user_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    reservation_id BIGINT,
    stars INT NOT NULL,
    comment VARCHAR(500),
    review_token VARCHAR(60) NOT NULL UNIQUE,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    type VARCHAR(20) NOT NULL DEFAULT 'WAREHOUSE',
    CONSTRAINT chk_stars CHECK (stars >= 1 AND stars <= 5),
    CONSTRAINT chk_rating_type CHECK (type IN ('WAREHOUSE', 'SERVICE')),
    CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_rating_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT fk_rating_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(id)
);

CREATE INDEX idx_ratings_user ON ratings(user_id);
CREATE INDEX idx_ratings_warehouse ON ratings(warehouse_id);
CREATE INDEX idx_ratings_reservation ON ratings(reservation_id);
CREATE INDEX idx_ratings_type ON ratings(type);
CREATE INDEX idx_ratings_verified ON ratings(verified);
