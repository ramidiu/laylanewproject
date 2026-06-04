package com.remitz.modules.payout.usi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "usi_remitters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsiRemitterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** users.id of the Layla customer this remitter mirrors. */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** USI-side remitter id returned by createRemitter. */
    @Column(name = "remitter_id", length = 64, nullable = false, unique = true)
    private String remitterId;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "verified")
    private Boolean verified;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
