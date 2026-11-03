package com.financialtransaction.reconciliation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "reconciliation_exceptions",
        indexes = {
                @Index(
                        name = "idx_recon_exception_run_id",
                        columnList = "reconciliation_run_id"
                ),
                @Index(
                        name = "idx_recon_exception_transfer_id",
                        columnList = "transfer_id"
                ),
                @Index(
                        name = "idx_recon_exception_status",
                        columnList = "status"
                )
        }
)
public class ReconciliationException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_run_id", nullable = false)
    private Reconciliation reconciliationRun;

    @Column(name = "transfer_id")
    private Long transferId;

    @Column(name = "transaction_id", length = 36)
    private String transactionId;

    @Column(name = "exception_type", nullable = false, length = 100)
    private String exceptionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExceptionStatus status;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }

        if (status == null) {
            status = ExceptionStatus.OPEN;
        }
    }

    public enum ExceptionStatus {
        OPEN,
        INVESTIGATING,
        RESOLVED
    }
}

