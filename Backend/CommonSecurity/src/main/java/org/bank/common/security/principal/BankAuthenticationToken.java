package org.bank.common.security.principal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

import java.util.Collection;
import java.util.Map;

/** Authentication whose principal is an {@link AuthenticatedUser} rather than the raw {@link Jwt}. */
public class BankAuthenticationToken extends AbstractOAuth2TokenAuthenticationToken<Jwt> {

    public BankAuthenticationToken(Jwt jwt, AuthenticatedUser principal,
                                   Collection<? extends GrantedAuthority> authorities) {
        super(jwt, principal, jwt, authorities);
        setAuthenticated(true);
    }

    @Override
    public Map<String, Object> getTokenAttributes() {
        return getToken().getClaims();
    }
}
