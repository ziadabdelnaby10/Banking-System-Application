package org.bank.account.config;

import org.bank.common.security.principal.AuthenticatedUser;
import org.bank.common.security.principal.CurrentUser;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableJpaAuditing(auditorAwareRef = "auditorProvider", dateTimeProviderRef = "auditingDateTimeProvider")
public class AccountConfiguration {

    /** Single time source (UTC) so time-dependent logic is deterministic in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** The auditor is the authenticated user's id, the same value the token carries in {@code sub}. */
    @Bean
    AuditorAware<UUID> auditorProvider() {
        return () -> CurrentUser.get().map(AuthenticatedUser::userId);
    }

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(OffsetDateTime.now(clock));
    }
}
