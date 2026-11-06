package org.bank.identity.controller;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/** Publishes the public signing key so other services (or the gateway) can verify access tokens. */
@RestController
@RequiredArgsConstructor
@Tag(name = "JWKS", description = "Public signing key used by other services to verify access tokens.")
public class JwksController {

    private final JWKSet publicJwkSet;

    @Operation(summary = "Public signing keys (JWK Set)", description = "Public, cacheable for 5 minutes.")
    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(publicJwkSet.toJSONObject());
    }
}
