package com.tuempresa.storage.warehouses.domain;

import com.tuempresa.storage.users.domain.User;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "favorite_warehouses", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "warehouse_id" }))
public class FavoriteWarehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FavoriteWarehouse() {
    }

    public FavoriteWarehouse(User user, Warehouse warehouse) {
        this.user = user;
        this.warehouse = warehouse;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
