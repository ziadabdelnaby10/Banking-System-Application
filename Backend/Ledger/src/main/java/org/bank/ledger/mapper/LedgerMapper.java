package org.bank.ledger.mapper;

import org.bank.common.dto.response.JournalBatchResponse;
import org.bank.common.dto.response.LedgerBalanceResponse;
import org.bank.common.dto.response.LedgerEntryResponse;
import org.bank.ledger.model.AccountBalance;
import org.bank.ledger.model.JournalBatch;
import org.bank.ledger.model.LedgerEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Entity to wire-format mapping. Entries are passed in explicitly because the batch has no association to them. */
@Mapper(config = MapStructConfig.class)
public interface LedgerMapper {

    @Mapping(target = "id", source = "batch.id")
    @Mapping(target = "batchRef", source = "batch.batchRef")
    @Mapping(target = "sourceService", source = "batch.sourceService")
    @Mapping(target = "sourceEvent", source = "batch.sourceEvent")
    @Mapping(target = "status", source = "batch.status")
    @Mapping(target = "postedBy", source = "batch.postedBy")
    @Mapping(target = "postedAt", source = "batch.postedAt")
    @Mapping(target = "entries", source = "entries")
    JournalBatchResponse toResponse(JournalBatch batch, List<LedgerEntry> entries);

    LedgerEntryResponse toEntry(LedgerEntry entry);

    @Mapping(target = "asOf", source = "balanceAsOf")
    LedgerBalanceResponse toBalance(AccountBalance balance);
}
