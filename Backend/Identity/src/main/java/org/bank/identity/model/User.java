package org.bank.identity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.bank.common.dto.enums.UserStatus;
import org.bank.common.util.UuidV7Generator;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.proxy.HibernateProxy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "users", indexes = {
        @Index(name = "users_lockout_idx", columnList = "status, locked_until, failed_login_count"),
        @Index(name = "users_branch_status_idx", columnList = "branch_code, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "users_username_key", columnNames = {"username"}),
        @UniqueConstraint(name = "users_email_key", columnNames = {"email"})
})
public class User {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "username", nullable = false, unique = true, columnDefinition = "citext")
    private String username;

    @NotNull
    @Column(name = "email", nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Size(max = 255)
    @NotNull
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @NotNull
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @NotNull
    @Builder.Default
    @ColumnDefault("false")
    @Column(name = "mfa_enabled", nullable = false)
    private Boolean mfaEnabled = false;

    @NotNull
    @Builder.Default
    @ColumnDefault("0")
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "last_failed_login_at")
    private OffsetDateTime lastFailedLoginAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Size(max = 20)
    @Column(name = "branch_code", length = 20)
    private String branchCode;

    // No @NotNull on generated timestamps: bean validation runs before Hibernate fills them in.
    // The NOT NULL column constraint still protects the data.
    @CreationTimestamp
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // Batch size avoids N+1 when a page of users is mapped to responses (roles are read per user).
    @BatchSize(size = 50)
    @Builder.Default
    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private Set<UserRole> userRoles = new HashSet<>();

    // ---- Lockout / lifecycle rules (kept in the domain so every caller applies them identically) ----

    /** True while an automatic lockout (too many failed logins) is still in effect. */
    public boolean isTemporarilyLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a failed login. Once {@code maxAttempts} consecutive failures are reached the user is locked
     * for {@code lockDuration}; the status stays ACTIVE so the lock expires by itself without a cleanup job.
     *
     * @return true if this failure triggered a lockout
     */
    public boolean registerFailedLogin(OffsetDateTime now, int maxAttempts, Duration lockDuration) {
        // A previous lock that already expired starts a fresh counting window.
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        lastFailedLoginAt = now;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(OffsetDateTime now) {
        resetFailedLogins();
        lastLoginAt = now;
    }

    /** Called once the password was proven correct; the login itself may still await a second factor. */
    public void resetFailedLogins() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    /** Administrative lock: stays in effect until an administrator unlocks the user. */
    public void lock() {
        status = UserStatus.LOCKED;
    }

    public void disable() {
        status = UserStatus.DISABLED;
    }

    /** Re-activates the user and clears any automatic lockout state. */
    public void activate() {
        status = UserStatus.ACTIVE;
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public void addRole(Role role) {
        if (role == null || role.getId() == null) {
            throw new IllegalArgumentException("Role and role id are required");
        }

        boolean alreadyAssigned = userRoles.stream()
                .anyMatch(userRole -> userRole.getRole() != null && role.getId().equals(userRole.getRole().getId()));
        if (alreadyAssigned) {
            return;
        }

        UserRole userRole = new UserRole();
        userRole.setId(UserRoleId.builder().userId(this.id).roleId(role.getId()).build());
        userRole.setUser(this);
        userRole.setRole(role);
        userRole.setAssignedAt(OffsetDateTime.now());
        userRoles.add(userRole);
    }

    public void removeRole(Long roleId) {
        if (roleId == null) {
            return;
        }

        userRoles.removeIf(userRole -> {
            boolean matches = userRole.getRole() != null && roleId.equals(userRole.getRole().getId());
            if (matches) {
                userRole.setUser(null);
                userRole.setRole(null);
            }
            return matches;
        });
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UuidV7Generator.generateUUIDV7();
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        User user = (User) o;
        return getId() != null && Objects.equals(getId(), user.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() : getClass().hashCode();
    }
}