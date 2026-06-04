package com.remitz.modules.payout.usi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usi_beneficiaries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsiBeneficiaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** beneficiaries.id (as string for legacy parity). */
    @Column(name = "local_beneficiary_id", length = 64, nullable = false, unique = true)
    private String localBeneficiaryId;

    /** USI-side beneficiary id from createBeneficiary. */
    @Column(name = "usi_beneficiary_id", length = 64, nullable = false, unique = true)
    private String usiBeneficiaryId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
