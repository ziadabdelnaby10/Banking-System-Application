package org.bank.account.repository;

import org.bank.account.model.AccountStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, UUID> {

    Page<AccountStatusHistory> findByAccountId(UUID accountId, Pageable pageable);
}
