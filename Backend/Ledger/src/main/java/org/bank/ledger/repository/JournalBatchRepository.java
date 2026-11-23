package org.bank.ledger.repository;

import org.bank.ledger.model.JournalBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JournalBatchRepository extends JpaRepository<JournalBatch, UUID> {

    /** Idempotency lookup, served by the unique index on {@code batch_ref}. */
    Optional<JournalBatch> findByBatchRef(String batchRef);
}
