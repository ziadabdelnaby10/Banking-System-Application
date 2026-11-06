package org.bank.common.security.correlation;

import org.slf4j.MDC;

import java.util.regex.Pattern;

/** Correlation id conventions shared by the HTTP filter, logging and event metadata. */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    /** Matches the 64 character column limit used for correlation ids in the schema. */
    static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationIds() {
    }

    public static boolean isValid(String candidate) {
        return candidate != null && VALID.matcher(candidate).matches();
    }

    /** The id of the request being processed on this thread, or null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
