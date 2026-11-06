package org.bank.identity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.bank.common.dto.enums.MfaMethod;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "mfa_challenges", indexes = {
        @Index(name = "mfa_challenges_user_idx", columnList = "user_id, created_at"),
        @Index(name = "mfa_challenges_correlation_idx", columnList = "correlation_id")
})
public class MfaChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "mfa_challenges_id_gen")
    @SequenceGenerator(name = "mfa_challenges_id_gen", sequenceName = "mfa_challenges_id_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Size(max = 64)
    @NotNull
    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private MfaMethod method;

    @Size(max = 255)
    @NotNull
    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    /** Failed verification attempts; the challenge is burned after too many (see V3 migration). */
    @NotNull
    @Builder.Default
    @ColumnDefault("0")
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public boolean isUsable(OffsetDateTime now, int maxAttempts) {
        return verifiedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
    }
}