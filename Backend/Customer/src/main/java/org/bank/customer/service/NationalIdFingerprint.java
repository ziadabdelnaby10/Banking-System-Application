package org.bank.customer.service;

import org.bank.common.error.ErrorCode;
import org.bank.common.exception.BankException;
import org.bank.customer.config.CustomerProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Turns a national id into a keyed hash (HMAC-SHA-256). The Customer database only ever stores this
 * fingerprint plus the last four characters, so a leaked table does not leak national ids, yet two
 * registrations of the same person still collide on the unique constraint.
 */
@Component
public class NationalIdFingerprint {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int VISIBLE_CHARACTERS = 4;

    private final SecretKeySpec key;

    public NationalIdFingerprint(CustomerProperties properties) {
        this.key = new SecretKeySpec(properties.nationalIdPepper().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** Removes separators and case so "a-123 456" and "A123456" are the same person. */
    public static String normalize(String rawNationalId) {
        return rawNationalId.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static String lastCharacters(String normalizedNationalId) {
        int length = normalizedNationalId.length();
        return normalizedNationalId.substring(Math.max(0, length - VISIBLE_CHARACTERS));
    }

    public String fingerprint(String normalizedNationalId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalizedNationalId.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            // Cannot happen for HmacSHA256 with a non-empty key; never echo key material.
            throw new BankException(ErrorCode.INTERNAL_ERROR, "Could not fingerprint national id");
        }
    }
}
