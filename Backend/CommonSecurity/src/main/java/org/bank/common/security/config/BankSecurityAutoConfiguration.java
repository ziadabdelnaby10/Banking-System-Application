package org.bank.common.security.config;

import jakarta.servlet.Filter;
import org.bank.common.security.access.BranchAccessEvaluator;
import org.bank.common.security.correlation.CorrelationIdFilter;
import org.bank.common.security.jwt.BankJwtDecoders;
import org.bank.common.security.principal.BankJwtAuthenticationConverter;
import org.bank.common.security.web.ApiAccessDeniedHandler;
import org.bank.common.security.web.ApiAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Zero-boilerplate security for servlet microservices: a stateless JWT resource server with
 * consistent JSON errors, method security, branch scoping and correlation ids.
 * Every bean backs off when the service defines its own, so any piece can be overridden.
 */
@AutoConfiguration(beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtDecoder.class, JsonMapper.class})
@ConditionalOnProperty(prefix = "bank.security", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(BankSecurityProperties.class)
@EnableMethodSecurity
public class BankSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    FilterRegistrationBean<Filter> correlationIdFilterRegistration() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        // Runs before the Spring Security chain so even 401/403 responses carry the correlation id.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    ApiAuthenticationEntryPoint apiAuthenticationEntryPoint(JsonMapper jsonMapper) {
        return new ApiAuthenticationEntryPoint(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiAccessDeniedHandler apiAccessDeniedHandler(JsonMapper jsonMapper) {
        return new ApiAccessDeniedHandler(jsonMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    BankJwtAuthenticationConverter bankJwtAuthenticationConverter() {
        return new BankJwtAuthenticationConverter();
    }

    @Bean("branchAccess")
    @ConditionalOnMissingBean
    BranchAccessEvaluator branchAccessEvaluator(BankSecurityProperties properties) {
        return new BranchAccessEvaluator(properties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder bankJwtDecoder(BankSecurityProperties properties) {
        return BankJwtDecoders.create(properties.jwt());
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    SecurityFilterChain bankSecurityFilterChain(HttpSecurity http,
                                                BankSecurityProperties properties,
                                                BankJwtAuthenticationConverter converter,
                                                ApiAuthenticationEntryPoint entryPoint,
                                                ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        String[] publicPaths = properties.publicPaths().toArray(String[]::new);
        return http
                // Stateless bearer-token API: no cookies, therefore no CSRF surface.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(publicPaths).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }
}
