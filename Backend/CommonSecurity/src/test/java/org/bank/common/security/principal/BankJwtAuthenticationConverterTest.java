package org.bank.common.security.principal;

import org.bank.common.security.token.TokenClaims;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankJwtAuthenticationConverterTest {

    private final BankJwtAuthenticationConverter converter = new BankJwtAuthenticationConverter();

    private static Jwt.Builder jwt(String subject) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void mapsRolesToRolePrefixedAuthoritiesAndPermissionsToPlainAuthorities() {
        UUID id = UUID.randomUUID();
        Jwt token = jwt(id.toString())
                .claim(TokenClaims.USERNAME, "alice")
                .claim(TokenClaims.BRANCH, "BR001")
                .claim(TokenClaims.ROLES, java.util.List.of("TELLER"))
                .claim(TokenClaims.PERMISSIONS, java.util.List.of("USER_VIEW"))
                .claim(TokenClaims.MFA, true)
                .claim(TokenClaims.SESSION_ID, "42")
                .build();

        BankAuthenticationToken auth = converter.convert(token);

        AuthenticatedUser user = (AuthenticatedUser) auth.getPrincipal();
        assertEquals(id, user.userId());
        assertEquals("alice", user.username());
        assertEquals("BR001", user.branchCode());
        assertEquals("42", user.sessionId());
        assertTrue(user.mfaVerified());
        assertEquals(Set.of("ROLE_TELLER", "USER_VIEW"),
                auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
        assertEquals(id.toString(), auth.getName());
    }

    @Test
    void missingClaimsYieldEmptyAuthoritiesAndNoMfa() {
        BankAuthenticationToken auth = converter.convert(jwt(UUID.randomUUID().toString())
                .claims(c -> c.putAll(Map.of("x", "y"))).build());

        assertTrue(auth.getAuthorities().isEmpty());
        assertFalse(((AuthenticatedUser) auth.getPrincipal()).mfaVerified());
    }

    @Test
    void nonUuidSubjectIsAnInvalidBearerToken() {
        assertThrows(InvalidBearerTokenException.class, () -> converter.convert(jwt("not-a-uuid").build()));
    }
}
