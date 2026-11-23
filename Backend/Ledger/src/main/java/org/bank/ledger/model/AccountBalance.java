package org.bank.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bank.common.dto.enums.LedgerAccountKind;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read model of an account's balance. It is never modified through the entity: postings change it with one atomic
 * conditional UPDATE ({@code AccountBalanceRepository#applyDelta}), which is what makes concurrent postings safe
 * without optimistic-lock retries. {@code balanceVersion} increases by one on every posting that touches the account.
 */
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "account_balances")
public class AccountBalance {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_kind", nullable = false, updatable = false, length = 10)
    private LedgerAccountKind accountKind;

    @Column(name = "currency", nullable = false, updatable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerBalance;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal availableBalance;

    @Column(name = "balance_version", nullable = false)
    private long balanceVersion;

    @Column(name = "balance_as_of", nullable = false)
    private OffsetDateTime balanceAsOf;
}
