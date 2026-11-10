package org.bank.customer.service;

import lombok.RequiredArgsConstructor;
import org.bank.common.exception.ResourceNotFoundException;
import org.bank.customer.model.Customer;
import org.bank.customer.repository.CustomerRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Loads a customer the caller is allowed to see (one query); anything else is "not found". */
@Component
@RequiredArgsConstructor
public class CustomerLookup {

    private final CustomerRepository customers;
    private final BranchScopePolicy scopePolicy;

    public Customer requireVisible(UUID customerId) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        scopePolicy.requireVisible("Customer", customerId, customer.getBranchCode());
        return customer;
    }
}
