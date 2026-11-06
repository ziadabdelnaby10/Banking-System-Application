package org.bank.identity.mapper;

import org.bank.common.dto.response.LoginAttemptResponse;
import org.bank.identity.model.LoginAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.net.InetAddress;

@Mapper(config = MapStructConfig.class)
public interface LoginAttemptMapper {

    @Mapping(target = "ipAddress", source = "ipAddress", qualifiedByName = "hostAddress")
    LoginAttemptResponse toResponse(LoginAttempt attempt);

    /** InetAddress.toString() prefixes "hostname/", which is noise (and a reverse-DNS leak) in an API. */
    @Named("hostAddress")
    default String hostAddress(InetAddress address) {
        return address == null ? null : address.getHostAddress();
    }
}
