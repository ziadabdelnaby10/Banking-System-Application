package org.bank.common.security.jwt;

import org.bank.common.security.testsupport.TestTokens;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PemKeysTest {

    private static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + type + "-----\n";
    }

    @Test
    void roundTripsPublicAndPrivateKeys() {
        KeyPair pair = TestTokens.generateKeyPair();

        RSAPublicKey publicKey = PemKeys.parsePublicKey(pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        RSAPrivateKey privateKey = PemKeys.parsePrivateKey(pem("PRIVATE KEY", pair.getPrivate().getEncoded()));

        assertEquals(((RSAPublicKey) pair.getPublic()).getModulus(), publicKey.getModulus());
        assertEquals(publicKey.getModulus(), PemKeys.derivePublicKey(privateKey).getModulus());
    }

    @Test
    void rejectsMissingPemBlock() {
        assertThrows(IllegalArgumentException.class, () -> PemKeys.parsePublicKey("garbage"));
    }

    @Test
    void rejectsPrivateKeyWhenPublicExpected() {
        KeyPair pair = TestTokens.generateKeyPair();
        assertThrows(IllegalArgumentException.class,
                () -> PemKeys.parsePublicKey(pem("PUBLIC KEY", pair.getPrivate().getEncoded())));
    }
}
