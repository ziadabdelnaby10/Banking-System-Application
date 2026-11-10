package org.bank.customer.repository;

import org.bank.customer.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    boolean existsByNationalIdFingerprint(String nationalIdFingerprint);

    /** Gap-tolerant, race-free source of customer numbers (a rolled-back registration just skips a number). */
    @Query(value = "select nextval('customer_no_seq')", nativeQuery = true)
    long nextCustomerSequence();
}
