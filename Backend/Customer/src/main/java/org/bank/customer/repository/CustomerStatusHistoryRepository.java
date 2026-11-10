package org.bank.customer.repository;

import org.bank.customer.model.CustomerStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CustomerStatusHistoryRepository extends JpaRepository<CustomerStatusHistory, UUID> {
}
