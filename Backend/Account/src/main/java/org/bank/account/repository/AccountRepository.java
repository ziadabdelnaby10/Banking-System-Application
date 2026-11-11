package org.bank.account.repository;

import org.bank.account.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    @Query(value = "select nextval('account_no_seq')", nativeQuery = true)
    long nextAccountSequence();

    /**
     * Replaces the balance read-model with a Ledger snapshot in one statement. The {@code balance_version} guard
     * makes it safe against duplicated and out-of-order deliveries without a read or an optimistic-lock retry.
     *
     * @return 1 when applied, 0 when the account is unknown or the snapshot is not newer than the stored one
     */
    @Modifying
    @Query(value = """
            update accounts
               set ledger_balance = :ledgerBalance,
                   available_balance = :availableBalance,
                   balance_version = :balanceVersion,
                   balance_as_of = :asOf,
                   updated_at = :asOf,
                   version = version + 1
             where id = :id
               and balance_version < :balanceVersion
            """, nativeQuery = true)
    int applyBalanceSnapshot(@Param("id") UUID id,
                             @Param("ledgerBalance") BigDecimal ledgerBalance,
                             @Param("availableBalance") BigDecimal availableBalance,
                             @Param("balanceVersion") long balanceVersion,
                             @Param("asOf") OffsetDateTime asOf);
}
