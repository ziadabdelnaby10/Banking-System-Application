package org.bank.identity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.bank.common.dto.enums.LoginResult;
import org.bank.common.util.UuidV7Generator;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "login_attempts_user_created_idx", columnList = "user_id, created_at"),
        @Index(name = "login_attempts_username_created_idx", columnList = "username, created_at"),
        @Index(name = "login_attempts_correlation_idx", columnList = "correlation_id")
})
public class LoginAttempt {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "username", columnDefinition = "citext")
    private String username;

    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Size(max = 255)
    @Column(name = "user_agent")
    private String userAgent;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private LoginResult result;

    @Size(max = 80)
    @Column(name = "failure_reason", length = 80)
    private String failureReason;

    @Size(max = 64)
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UuidV7Generator.generateUUIDV7();
        }
    }
}