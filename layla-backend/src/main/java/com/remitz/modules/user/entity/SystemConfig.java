package com.remitz.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "config_value", length = 500)
    private String configValue;

    // Code added by Naresh: System Controls Phase 1 — typed-config foundation.
    // These columns are read-only for now (no service-layer behavior change in this phase).
    @Column(name = "value_type", length = 20, nullable = false)
    private String valueType;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "allowed_values", columnDefinition = "TEXT")
    private String allowedValues;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        // Code added by Naresh: mirror the DB column defaults so new rows inserted via JPA
        // (rare — seeding is via Flyway today) stay consistent with Phase 1 schema rules.
        if (this.valueType == null) this.valueType = "STRING";
        if (this.category == null) this.category = "general";
        if (this.version == null) this.version = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
