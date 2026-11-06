package org.bank.identity.mapper;

import org.bank.common.dto.response.RoleResponse;
import org.bank.identity.model.Role;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface RoleMapper {

    RoleResponse toResponse(Role role);

    List<RoleResponse> toResponses(List<Role> roles);
}
