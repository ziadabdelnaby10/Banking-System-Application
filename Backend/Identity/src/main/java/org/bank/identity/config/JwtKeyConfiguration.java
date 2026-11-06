package org.bank.identity.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.bank.common.security.config.BankSecurityProperties;
import org.bank.common.security.jwt.BankJwtDecoders;
import org.bank.common.security.jwt.PemKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * Identity is the only service holding the RSA private key. Other services verify with the public key
 * (or the JWKS endpoint), so a compromised downstream service can never mint tokens.
 */
@Configuration(proxyBeanMethods = false)
public class JwtKeyConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfiguration.class);

    @Bean
    RSAKey signingKey(IdentityProperties properties) {
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        IdentityProperties.Token token = properties.token();
        if (token.privateKeyLocation() != null) {
            privateKey = PemKeys.readPrivateKey(token.privateKeyLocation());
            publicKey = PemKeys.derivePublicKey(privateKey);
        } else if (token.allowEphemeralKey()) {
            log.warn("No bank.identity.token.private-key-location configured: using an EPHEMERAL signing key. "
                    + "Tokens become invalid on restart. This is only acceptable for local development.");
            KeyPair pair = generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            publicKey = (RSAPublicKey) pair.getPublic();
        } else {
            throw new IllegalStateException("bank.identity.token.private-key-location must be configured");
        }
        try {
            RSAKey.Builder builder = new RSAKey.Builder(publicKey).privateKey(privateKey);
            // The RFC 7638 thumbprint gives a stable kid derived from the key itself.
            String keyId = builder.build().computeThumbprint().toString();
            return builder.keyID(keyId).build();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Cannot compute JWK thumbprint", ex);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey signingKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    /** Public JWK set served at /.well-known/jwks.json (private parameters are stripped on serialisation). */
    @Bean
    JWKSet publicJwkSet(RSAKey signingKey) {
        return new JWKSet(signingKey.toPublicJWK());
    }

    /** Identity validates its own tokens with the same rules every other service applies. */
    @Bean
    JwtDecoder jwtDecoder(RSAKey signingKey, BankSecurityProperties securityProperties) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(signingKey.toRSAPublicKey()).build();
            decoder.setJwtValidator(BankJwtDecoders.validator(securityProperties.jwt()));
            return decoder;
        } catch (JOSEException ex) {
            throw new IllegalStateException("Cannot build JWT decoder from signing key", ex);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
