package org.bank.customer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bank.common.dto.enums.BranchStatus;
import org.bank.common.util.UuidV7Generator;
import java.util.Locale;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "branches")
public class Branch extends AuditableEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Canonical upper-case code; equals the {@code branch} claim in access tokens. */
    @NotNull
    @Size(max = 20)
    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @NotNull
    @Size(max = 150)
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Size(max = 100)
    @Column(name = "region", length = 100)
    private String region;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BranchStatus status = BranchStatus.ACTIVE;

    public static Branch open(String code, String name, String region) {
        Branch branch = new Branch();
        branch.code = normalizeCode(code);
        branch.name = name.trim();
        branch.region = blankToNull(region);
        return branch;
    }

    public static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isActive() {
        return status == BranchStatus.ACTIVE;
    }

    /** PATCH semantics: null means "leave unchanged". */
    public void update(String newName, String newRegion) {
        if (newName != null) {
            this.name = newName.trim();
        }
        if (newRegion != null) {
            this.region = blankToNull(newRegion);
        }
    }

    public void changeStatus(BranchStatus newStatus) {
        this.status = newStatus;
    }

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UuidV7Generator.generateUUIDV7();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
