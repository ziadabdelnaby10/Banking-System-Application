package org.bank.customer.service;

import org.bank.common.dto.response.PageResponse;
import org.bank.common.dto.request.ChangeCustomerStatusRequest;
import org.bank.common.dto.response.CustomerResponse;
import org.bank.common.dto.response.CustomerSummaryResponse;
import org.bank.common.dto.request.RegisterCustomerRequest;
import org.bank.common.dto.request.UpdateCustomerRequest;
import org.bank.customer.repository.CustomerSearchCriteria;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerService {

    CustomerResponse register(RegisterCustomerRequest request);

    CustomerResponse get(UUID customerId);

    PageResponse<CustomerSummaryResponse> search(CustomerSearchCriteria criteria, Pageable pageable);

    CustomerResponse update(UUID customerId, UpdateCustomerRequest request);

    CustomerResponse changeStatus(UUID customerId, ChangeCustomerStatusRequest request);
}
