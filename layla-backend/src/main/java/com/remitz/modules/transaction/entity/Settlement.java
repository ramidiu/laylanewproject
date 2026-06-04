package com.remitz.modules.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_type", nullable = false)
    private String settlementType;

    @Column(name = "from_party", nullable = false)
    private String fromParty;

    @Column(name = "from_party_id")
    private Long fromPartyId;

    @Column(name = "to_party", nullable = false)
    private String toParty;

    @Column(name = "to_party_id")
    private Long toPartyId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reference")
    private String reference;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "initiated_by")
    private String initiatedBy;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
