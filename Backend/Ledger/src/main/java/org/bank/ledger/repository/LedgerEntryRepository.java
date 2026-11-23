package org.bank.ledger.repository;

import org.bank.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {

    /** Entries of one batch in posting order, served by the unique index on {@code (batch_id, line_no)}. */
    List<LedgerEntry> findByBatchIdOrderByLineNoAsc(UUID batchId);
}
