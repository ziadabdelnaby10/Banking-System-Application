package org.bank.identity.service;

import java.net.InetAddress;

/**
 * Request metadata captured at the edge and passed to the service layer, which therefore never
 * depends on servlet types.
 *
 * @param ipAddress     caller address, null when it cannot be determined
 * @param userAgent     raw User-Agent header, may be null
 * @param correlationId X-Correlation-Id of the request (never null)
 */
public record ClientContext(InetAddress ipAddress, String userAgent, String correlationId) {
}
