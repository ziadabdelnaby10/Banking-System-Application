package org.bank.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.bank.common.security.correlation.CorrelationIds;
import org.bank.identity.service.ClientContext;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;

/** Builds the servlet-free {@link ClientContext} the service layer works with. */
final class ClientContexts {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    private ClientContexts() {
    }

    static ClientContext from(HttpServletRequest request) {
        String correlationId = CorrelationIds.current();
        return new ClientContext(
                parseAddress(request.getRemoteAddr()),
                truncate(request.getHeader("User-Agent")),
                correlationId != null ? correlationId : UUID.randomUUID().toString());
    }

    private static InetAddress parseAddress(String remoteAddress) {
        if (remoteAddress == null) {
            return null;
        }
        try {
            // The servlet container always supplies a literal IP, so no DNS lookup happens here.
            return InetAddress.getByName(remoteAddress);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static String truncate(String value) {
        return value != null && value.length() > MAX_USER_AGENT_LENGTH ? value.substring(0, MAX_USER_AGENT_LENGTH) : value;
    }
}
