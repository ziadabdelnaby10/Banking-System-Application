package org.bank.identity.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokensTest {

    @Test
    void formatAndParseRoundTrip() {
        String secret = OpaqueTokens.newSecret();

        OpaqueTokens.Parsed parsed = OpaqueTokens.parse(OpaqueTokens.format(42L, secret)).orElseThrow();

        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.secret()).isEqualTo(secret);
    }

    @Test
    void secretsAreUnique() {
        assertThat(OpaqueTokens.newSecret()).isNotEqualTo(OpaqueTokens.newSecret());
    }

    @Test
    void parseRejectsMalformedInput() {
        assertThat(OpaqueTokens.parse(null)).isEmpty();
        assertThat(OpaqueTokens.parse("")).isEmpty();
        assertThat(OpaqueTokens.parse("nodot")).isEmpty();
        assertThat(OpaqueTokens.parse(".secret")).isEmpty();
        assertThat(OpaqueTokens.parse("12.")).isEmpty();
        assertThat(OpaqueTokens.parse("abc.secret")).isEmpty();
        assertThat(OpaqueTokens.parse("1." + "x".repeat(300))).isEmpty();
    }

    @Test
    void hashMatchesOnlyTheSameValue() {
        String hash = OpaqueTokens.sha256Hex("secret");

        assertThat(hash).hasSize(64);
        assertThat(OpaqueTokens.hashMatches("secret", hash)).isTrue();
        assertThat(OpaqueTokens.hashMatches("Secret", hash)).isFalse();
    }
}
