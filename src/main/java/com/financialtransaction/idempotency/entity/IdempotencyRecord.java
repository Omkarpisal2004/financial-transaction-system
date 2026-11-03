package com.financialtransaction.idempotency.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_idempotency_key",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(
                        name = "idx_idempotency_created_at",
                        columnList = "created_at"
                )
        }
)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100
    )
    private String idempotencyKey;

    @Column(
            name = "request_hash",
            nullable = false,
            length = 64
    )
    private String requestHash;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = IdempotencyStatus.IN_PROGRESS;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}