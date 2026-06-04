package com.remitz.modules.fx.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "competitor_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompetitorRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "competitor_name", nullable = false, length = 100)
    private String competitorName;

    @Column(name = "send_currency", nullable = false, length = 3)
    private String sendCurrency;

    @Column(name = "receive_currency", nullable = false, length = 3)
    private String receiveCurrency;

    @Column(name = "customer_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal customerRate;

    @Column(name = "fee", precision = 18, scale = 4)
    private BigDecimal fee;

    @Column(name = "total_cost_per_unit", precision = 18, scale = 8)
    private BigDecimal totalCostPerUnit;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @PrePersist
    protected void onCreate() {
        if (capturedAt == null) {
            capturedAt = LocalDateTime.now();
        }
    }
}
