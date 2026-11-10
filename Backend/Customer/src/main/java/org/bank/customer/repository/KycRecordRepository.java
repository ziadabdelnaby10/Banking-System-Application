package org.bank.customer.repository;

import org.bank.customer.model.KycOutcome;
import org.bank.customer.model.KycRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycRecordRepository extends JpaRepository<KycRecord, UUID> {

    List<KycRecord> findByCustomerIdOrderBySubmittedAtDesc(UUID customerId);

    Optional<KycRecord> findByIdAndCustomerId(UUID id, UUID customerId);

    /** Two columns per record instead of whole entities: all that customer-level KYC status needs. */
    @Query("select new org.bank.customer.model.KycOutcome(k.documentType, k.status) from KycRecord k where k.customerId = :customerId")
    List<KycOutcome> findOutcomesByCustomerId(@Param("customerId") UUID customerId);
}
