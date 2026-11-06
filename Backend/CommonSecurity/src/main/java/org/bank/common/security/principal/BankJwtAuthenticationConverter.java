package org.bank.common.security.principal;

import org.bank.common.security.token.TokenClaims;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Maps a validated JWT to a {@link BankAuthenticationToken}.
 * Roles become {@code ROLE_*} authorities (for hasRole); permissions are plain authorities
 * (for hasAuthority), matching the Identity RBAC model.
 */
public class BankJwtAuthenticationConverter implements Converter<Jwt, BankAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";

    @Override
    public BankAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        Set<String> roles = claimAsSet(jwt, TokenClaims.ROLES);
        Set<String> permissions = claimAsSet(jwt, TokenClaims.PERMISSIONS);

        List<GrantedAuthority> authorities = new ArrayList<>(roles.size() + permissions.size());
        roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role)));
        permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));

        AuthenticatedUser principal = new AuthenticatedUser(
                userId,
                jwt.getClaimAsString(TokenClaims.USERNAME),
                jwt.getClaimAsString(TokenClaims.BRANCH),
                Set.copyOf(roles),
                Set.copyOf(permissions),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean(TokenClaims.MFA)),
                jwt.getClaimAsString(TokenClaims.SESSION_ID));
        return new BankAuthenticationToken(jwt, principal, authorities);
    }

    private static UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException | NullPointerException ex) {
            // Surfaces as 401 through the bearer-token entry point instead of a 500.
            throw new InvalidBearerTokenException("Token subject is not a valid user id", ex);
        }
    }

    private static Set<String> claimAsSet(Jwt jwt, String claim) {
        Collection<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }
}
