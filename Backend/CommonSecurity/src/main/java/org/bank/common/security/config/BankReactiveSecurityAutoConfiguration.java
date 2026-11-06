package org.bank.common.security.config;

import org.bank.common.security.principal.BankJwtAuthenticationConverter;
import org.bank.common.security.reactive.ApiServerAccessDeniedHandler;
import org.bank.common.security.reactive.ApiServerAuthenticationEntryPoint;
import org.bank.common.security.reactive.CorrelationIdWebFilter;
import org.bank.common.security.reactive.ReactiveBankJwtDecoders;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.server.WebFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * WHAT THIS FILE IS FOR (plain words)
 * -----------------------------------
 * This is the "security switch" for the API Gateway. The Gateway uses WebFlux (the non-blocking web stack),
 * while Identity uses Servlet (the classic stack). The older BankSecurityAutoConfiguration only works for
 * Servlet apps, so this class does the same job for WebFlux apps.
 *
 * "Auto-configuration" means: any app that adds the CommonSecurity jar gets all the beans below for free,
 * without writing any security code. It is listed in META-INF/spring/...AutoConfiguration.imports, which is
 * how Spring Boot discovers it.
 *
 * What it sets up for the Gateway:
 *  - requires a valid signed JWT (login token) on every request except the public paths;
 *  - answers failures with the shared JSON error format (401 / 403);
 *  - adds a correlation id to every request so one request can be traced across services.
 *
 * It only checks WHO the caller is (authentication). Whether the caller may do something (roles, branch)
 * is decided inside each service. Every bean uses @ConditionalOnMissingBean, so an app can replace any piece.
 */
// "beforeName": run before Spring Boot's own default reactive security setup, so ours wins and Boot's
// default (which would show a login form / random password) is not used.
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.reactive.ReactiveWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.reactive.ReactiveOAuth2ResourceServerWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration"
})
// Only active in a WebFlux (reactive) application; servlet services like Identity ignore this class.
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({SecurityWebFilterChain.class, ReactiveJwtDecoder.class, JsonMapper.class})
@ConditionalOnProperty(prefix = "bank.security", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(BankSecurityProperties.class)
public class BankReactiveSecurityAutoConfiguration {

    // Adds/propagates the X-Correlation-Id header on every request (see CorrelationIdWebFilter).
    @Bean
    @ConditionalOnMissingBean(CorrelationIdWebFilter.class)
    WebFilter correlationIdWebFilter() {
        return new CorrelationIdWebFilter();
    }

    // Called when a request has no/invalid token: produces the JSON 401 response.
    @Bean
    @ConditionalOnMissingBean
    ApiServerAuthenticationEntryPoint apiServerAuthenticationEntryPoint(JsonMapper jsonMapper) {
        return new ApiServerAuthenticationEntryPoint(jsonMapper);
    }

    // Called when the user is logged in but not allowed: produces the JSON 403 response.
    @Bean
    @ConditionalOnMissingBean
    ApiServerAccessDeniedHandler apiServerAccessDeniedHandler(JsonMapper jsonMapper) {
        return new ApiServerAccessDeniedHandler(jsonMapper);
    }

    // Turns a verified JWT into the app's own user object (username, branch, roles, permissions).
    @Bean
    @ConditionalOnMissingBean
    BankJwtAuthenticationConverter bankJwtAuthenticationConverter() {
        return new BankJwtAuthenticationConverter();
    }

    // The component that verifies the token signature, expiry and issuer using Identity's public key.
    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    ReactiveJwtDecoder bankReactiveJwtDecoder(BankSecurityProperties properties) {
        return ReactiveBankJwtDecoders.create(properties.jwt());
    }

    // The main rulebook: the ordered list of security checks every request goes through.
    // (Servlet equivalent: SecurityFilterChain.)
    @Bean
    @ConditionalOnMissingBean(SecurityWebFilterChain.class)
    SecurityWebFilterChain bankSecurityWebFilterChain(ServerHttpSecurity http,
                                                      BankSecurityProperties properties,
                                                      BankJwtAuthenticationConverter converter,
                                                      ApiServerAuthenticationEntryPoint entryPoint,
                                                      ApiServerAccessDeniedHandler accessDeniedHandler) {
        String[] publicPaths = properties.publicPaths().toArray(String[]::new);
        return http
                // Stateless bearer-token API: no cookies, no CSRF surface, nothing stored between requests.
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // Access rules, checked top to bottom:
                .authorizeExchange(exchanges -> exchanges
                        // Browsers send CORS preflights without credentials; the CORS handler answers them.
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        // Paths from bank.security.public-paths (e.g. login) need no token.
                        .pathMatchers(publicPaths).permitAll()
                        // Everything else needs a valid token.
                        .anyExchange().authenticated())
                // Read the "Authorization: Bearer <jwt>" header, verify it, and build the user from it.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter::convert)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Use our JSON error responses for any 401 / 403 raised by the chain.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
