package org.bank.identity.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The "security off" switch must be impossible to use in a production-like profile. */
class SecurityDisabledConfigurationTest {

    @Test
    void refusesToStartInProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        assertThatThrownBy(() -> new SecurityDisabledConfiguration(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only allowed");
    }

    @Test
    void refusesToStartWithoutAnyProfile() {
        assertThatThrownBy(() -> new SecurityDisabledConfiguration(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsInDevLocalAndTestProfiles() {
        for (String profile : new String[]{"dev", "local", "test"}) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profile);
            assertThatCode(() -> new SecurityDisabledConfiguration(env)).doesNotThrowAnyException();
        }
    }
}
