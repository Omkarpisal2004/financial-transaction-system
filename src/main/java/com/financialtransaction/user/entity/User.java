package com.financialtransaction.user.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Setter
@Getter
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_email", columnNames = "email")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, length = 100)
    private String name;


    @Column(nullable = false, length = 255)
    private String email;


    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;


    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = UserStatus.ACTIVE;
        }
        if (role == null) {
            role = UserRole.USER;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

}
