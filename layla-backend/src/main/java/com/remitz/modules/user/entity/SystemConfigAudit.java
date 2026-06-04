package com.remitz.modules.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Code added by Naresh: System Controls Phase 3 — immutable audit row written once per
 * successful system_config update. Never updated after insert.
 */
@Entity
@Table(name = "system_config_audit")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfigAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, length = 100)
    private String configKey;

    @Column(name = "old_value", length = 500)
    private String oldValue;

    @Column(name = "new_value", length = 500)
    private String newValue;

    @Column(name = "old_version")
    private Integer oldVersion;

    @Column(name = "new_version")
    private Integer newVersion;

    @Column(name = "changed_by", nullable = false, length = 255)
    private String changedBy;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "change_source", nullable = false, length = 50)
    private String changeSource;

    // Code added by Naresh: System Controls Phase 7 — optional operator reason for dangerous toggles.
    @Column(name = "reason", length = 500)
    private String reason;

    @PrePersist
    protected void onCreate() {
        if (this.changedAt == null) this.changedAt = LocalDateTime.now();
        if (this.changeSource == null) this.changeSource = "API";
    }
}
