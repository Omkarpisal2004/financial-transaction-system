 package com.financialtransaction.reconciliation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "reconciliation_runs",
        indexes = {
                @Index(
                        name = "idx_reconciliation_run_started_at",
                        columnList = "started_at"
                )
        }
)
public class Reconciliation{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "transfers_checked", nullable = false)
    private int transfersChecked;

    @Column(name = "reconciled_count", nullable = false)
    private int reconciledCount;

    @Column(name = "exception_count", nullable = false)
    private int exceptionCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public enum RunStatus {
        RUNNING,
        COMPLETED,
        FAILED
    }
}

