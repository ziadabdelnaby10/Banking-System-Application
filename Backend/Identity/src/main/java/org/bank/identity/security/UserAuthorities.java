package org.bank.identity.security;

import java.util.Set;

/** Role names and permission codes granted to a user at token-issue time. */
public record UserAuthorities(Set<String> roles, Set<String> permissions) {
}
