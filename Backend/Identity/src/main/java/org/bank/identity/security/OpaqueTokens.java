package org.bank.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Helpers for opaque credentials of the form {@code <id>.<secret>} (refresh tokens, MFA challenges).
 * The id locates the row; the 256-bit random secret is the actual credential and is stored only as a
 * SHA-256 hash. A fast hash is appropriate here because the secret is high-entropy, not a password.
 */
public final class OpaqueTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 200;

    private OpaqueTokens() {
    }

    public record Parsed(long id, String secret) {
    }

    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String format(long id, String secret) {
        return id + "." + secret;
    }

    public static Optional<Parsed> parse(String token) {
        if (token == null || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Parsed(Long.parseLong(token.substring(0, dot)), token.substring(dot + 1)));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is guaranteed by the JDK", ex);
        }
    }

    /** Constant-time comparison so response timing does not reveal how many hash characters matched. */
    public static boolean hashMatches(String value, String expectedHexHash) {
        return MessageDigest.isEqual(
                sha256Hex(value).getBytes(StandardCharsets.UTF_8),
                expectedHexHash.getBytes(StandardCharsets.UTF_8));
    }
}
