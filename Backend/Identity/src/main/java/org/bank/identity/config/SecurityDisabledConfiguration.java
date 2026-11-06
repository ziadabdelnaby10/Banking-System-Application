package org.bank.identity.config;

import jakarta.servlet.Filter;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.config.BankSecurityProperties;
import org.bank.common.security.correlation.CorrelationIdFilter;
import org.bank.common.security.principal.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;
import java.util.UUID;

/**
 * LOCAL TESTING SWITCH: turns authentication OFF for the Identity service.
 *
 * <p>How to use (local only): start the service with {@code bank.security.enabled=false}, for example
 * {@code SECURITY_ENABLED=false} (see application-dev.yml) or {@code --bank.security.enabled=false}.
 * Then every endpoint can be called without a token (Postman, curl, Swagger...).
 *
 * <p>Why a separate class is needed: when {@code bank.security.enabled=false} the shared CommonSecurity
 * starter switches itself off completely. Without this class Spring Boot would fall back to its own
 * default security (a generated password and HTTP basic), and beans the services still need
 * ({@code BranchAccessEvaluator}) would be missing. So here we:
 * <ul>
 *   <li>open every URL (no token required, no CSRF);</li>
 *   <li>treat every caller as a built-in local ADMIN ({@link #LOCAL_USER}), so code that asks
 *       "who is calling?" (audit logs, branch rules) keeps working;</li>
 *   <li>do NOT enable method security, so {@code @PreAuthorize} role checks are not applied;</li>
 *   <li>keep the correlation-id filter so logs and error bodies still carry a request id.</li>
 * </ul>
 *
 * <p>SAFETY: the application refuses to start with security disabled unless the {@code dev}, {@code local}
 * or {@code test} profile is active, so this can never be switched on in a production deployment by a
 * single stray property.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "bank.security", name = "enabled", havingValue = "false")
@EnableConfigurationProperties(BankSecurityProperties.class)
public class SecurityDisabledConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityDisabledConfiguration.class);

    /** Profiles in which security may be disabled. */
    static final String ALLOWED_PROFILES_HELP = "dev, local or test";

    /**
     * Identity every caller gets while security is disabled. It is NOT a row in the users table, so
     * endpoints that load "my own user record" (for example GET /api/v1/users/me) answer 404.
     */
    public static final AuthenticatedUser LOCAL_USER = new AuthenticatedUser(
            new UUID(0L, 0L), "local-admin", "LOCAL", Set.of("ADMIN"), Set.of(), true, "local");

    public SecurityDisabledConfiguration(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("dev", "local", "test"))) {
            throw new IllegalStateException("bank.security.enabled=false is only allowed when the "
                    + ALLOWED_PROFILES_HELP + " profile is active. Refusing to start with security disabled.");
        }
        log.warn("""

                ==========================================================================
                  SECURITY IS DISABLED (bank.security.enabled=false)
                  Every request is accepted and treated as the local ADMIN user.
                  Use for LOCAL TESTING only. Never enable this outside your machine.
                ==========================================================================""");
    }

    /** Same bean name the services and (disabled) annotations expect; recreated because the starter is off. */
    @Bean("branchAccess")
    @ConditionalOnMissingBean
    BranchAccessEvaluator branchAccessEvaluator(BankSecurityProperties properties) {
        return new BranchAccessEvaluator(properties);
    }

    /** Keeps the X-Correlation-Id header / log correlation working (the starter's own filter is off). */
    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    FilterRegistrationBean<Filter> correlationIdFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /** Allow everything, and make "anonymous" callers appear as the local ADMIN user. */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain permitAllSecurityFilterChain(HttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                // Requests without a token are "anonymous"; give them the local admin as principal.
                .anonymous(anonymous -> anonymous.principal(LOCAL_USER).authorities("ROLE_ADMIN"))
                .build();
    }
}
