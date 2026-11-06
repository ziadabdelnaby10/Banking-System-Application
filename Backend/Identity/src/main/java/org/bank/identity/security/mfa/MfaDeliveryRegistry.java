package org.bank.identity.security.mfa;

import org.bank.common.dto.enums.MfaMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Looks up the delivery strategy for an MFA method and fails closed when none is configured. */
@Component
public class MfaDeliveryRegistry {

    private final Map<MfaMethod, MfaCodeDelivery> deliveries = new EnumMap<>(MfaMethod.class);

    public MfaDeliveryRegistry(List<MfaCodeDelivery> strategies) {
        strategies.forEach(strategy -> deliveries.put(strategy.method(), strategy));
    }

    public MfaCodeDelivery forMethod(MfaMethod method) {
        MfaCodeDelivery delivery = deliveries.get(method);
        if (delivery == null) {
            throw new IllegalStateException("No MFA delivery channel configured for " + method);
        }
        return delivery;
    }
}
