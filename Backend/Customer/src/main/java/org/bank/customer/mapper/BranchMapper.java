package org.bank.customer.mapper;

import org.bank.common.dto.response.BranchResponse;
import org.bank.customer.model.Branch;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface BranchMapper {

    BranchResponse toResponse(Branch branch);
}
