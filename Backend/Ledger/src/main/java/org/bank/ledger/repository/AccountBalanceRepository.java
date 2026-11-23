package org.bank.ledger.repository;

import org.bank.ledger.model.AccountBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {

    Optional<AccountBalance> findByAccountId(UUID accountId);

    boolean existsByAccountId(UUID accountId);

    List<AccountBalance> findByAccountIdIn(Collection<UUID> accountIds);

    @Query("select b.currency from AccountBalance b where b.accountId = :accountId")
    Optional<String> findCurrencyByAccountId(@Param("accountId") UUID accountId);

    /**
     * Adds a signed amount to an account in one statement; the row lock it takes serialises concurrent postings on
     * the same account, so there is no read-modify-write race and no optimistic-lock retry.
     *
     * @return 1 when applied, 0 when the account is unknown, is held in another currency, or a customer account
     * would go negative
     */
    @Modifying
    @Query(value = """
            update account_balances
               set ledger_balance = ledger_balance + :delta,
                   available_balance = available_balance + :delta,
                   balance_version = balance_version + 1,
                   balance_as_of = :asOf,
                   updated_at = :asOf
             where account_id = :accountId
               and currency = :currency
               and (account_kind = 'SYSTEM'
                    or (ledger_balance + :delta >= 0 and available_balance + :delta >= 0))
            """, nativeQuery = true)
    int applyDelta(@Param("accountId") UUID accountId,
                   @Param("currency") String currency,
                   @Param("delta") BigDecimal delta,
                   @Param("asOf") OffsetDateTime asOf);

    /** Creates the zero balance row of an account's first posting; a concurrent creator wins silently. */
    @Modifying
    @Query(value = """
            insert into account_balances (id, account_id, account_kind, currency, ledger_balance, available_balance,
                                          balance_version, balance_as_of, created_at, updated_at)
            values (:id, :accountId, :kind, :currency, 0, 0, 0, :now, :now, :now)
            on conflict (account_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("accountId") UUID accountId,
                       @Param("kind") String kind,
                       @Param("currency") String currency,
                       @Param("now") OffsetDateTime now);

    /** Stored balance and the sum of its entries (credits minus debits) from one snapshot; empty if unknown. */
    @Query(value = """
            select b.currency as currency,
                   b.ledger_balance as stored,
                   coalesce((select sum(case e.entry_type when 'CREDIT' then e.amount else -e.amount end)
                               from ledger_entries e
                              where e.account_id = b.account_id), 0) as computed
              from account_balances b
             where b.account_id = :accountId
            """, nativeQuery = true)
    Optional<ReconciliationRow> reconcile(@Param("accountId") UUID accountId);
}
