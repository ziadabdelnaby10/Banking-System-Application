package org.bank.customer.mapper;

import org.bank.common.dto.response.KycRecordResponse;
import org.bank.customer.model.KycRecord;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface KycMapper {

    KycRecordResponse toResponse(KycRecord record);
}
