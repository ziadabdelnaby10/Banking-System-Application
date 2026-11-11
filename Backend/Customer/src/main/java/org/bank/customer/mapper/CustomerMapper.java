package org.bank.customer.mapper;

import org.bank.common.dto.response.CustomerResponse;
import org.bank.common.dto.response.CustomerSummaryResponse;
import org.bank.common.dto.response.KycRecordResponse;
import org.bank.customer.model.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/** Customers expose only a masked national id; the stored fingerprint never leaves the service. */
@Mapper(config = MapStructConfig.class)
public interface CustomerMapper {

    @Mapping(target = "nationalId", expression = "java(customer.maskedNationalId())")
    CustomerSummaryResponse toSummary(Customer customer);

    @Mapping(target = "nationalId", expression = "java(customer.maskedNationalId())")
    @Mapping(target = "branchName", source = "branchName")
    @Mapping(target = "kycRecords", source = "kycRecords")
    CustomerResponse toResponse(Customer customer, String branchName, List<KycRecordResponse> kycRecords);
}
