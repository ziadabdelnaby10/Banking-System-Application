package org.bank.common.security.jwt;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads RSA keys from PEM text. Supported formats: X.509 "BEGIN PUBLIC KEY" and PKCS#8
 * "BEGIN PRIVATE KEY" (convert PKCS#1 keys with {@code openssl pkcs8 -topk8 -nocrypt}).
 * Keys shorter than {@value #MIN_KEY_BITS} bits are rejected.
 */
public final class PemKeys {

    static final int MIN_KEY_BITS = 2048;

    private PemKeys() {
    }

    public static RSAPublicKey readPublicKey(Resource resource) {
        return parsePublicKey(read(resource));
    }

    public static RSAPrivateKey readPrivateKey(Resource resource) {
        return parsePrivateKey(read(resource));
    }

    public static RSAPublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decode(pem, "PUBLIC KEY");
            RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            requireStrongKey(key.getModulus());
            return key;
        } catch (GeneralSecurityException | ClassCastException ex) {
            throw new IllegalArgumentException("Invalid RSA public key (expected X.509 PEM)", ex);
        }
    }

    public static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decode(pem, "PRIVATE KEY");
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            requireStrongKey(key.getModulus());
            return key;
        } catch (GeneralSecurityException | ClassCastException ex) {
            throw new IllegalArgumentException("Invalid RSA private key (expected unencrypted PKCS#8 PEM)", ex);
        }
    }

    /** Derives the verification key from a CRT private key so only one secret needs to be configured. */
    public static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalArgumentException("Cannot derive public key: private key has no public exponent");
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Cannot derive public key", ex);
        }
    }

    private static void requireStrongKey(BigInteger modulus) {
        if (modulus.bitLength() < MIN_KEY_BITS) {
            throw new IllegalArgumentException("RSA key must be at least " + MIN_KEY_BITS + " bits");
        }
    }

    private static byte[] decode(String pem, String type) {
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = pem.indexOf(begin);
        int stop = pem.indexOf(end);
        if (start < 0 || stop < start) {
            throw new IllegalArgumentException("PEM block '" + type + "' not found");
        }
        String body = pem.substring(start + begin.length(), stop).replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String read(Resource resource) {
        try {
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read key resource " + resource.getDescription(), ex);
        }
    }
}
