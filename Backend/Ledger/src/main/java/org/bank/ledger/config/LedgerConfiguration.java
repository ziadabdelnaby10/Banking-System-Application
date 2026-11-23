package org.bank.ledger.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LedgerProperties.class)
public class LedgerConfiguration {

    /** Single time source (UTC) so time-dependent logic is deterministic in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
