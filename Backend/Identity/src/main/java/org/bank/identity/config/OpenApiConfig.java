package org.bank.identity.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

import java.util.List;

/**
 * OpenAPI description of the Identity service. The document is served at {@code /v3/api-docs}; the Swagger UI
 * is hosted by the API Gateway, which fetches this document through {@code /docs/identity/v3/api-docs}.
 *
 * <p>Both endpoints can be switched off with {@code springdoc.api-docs.enabled=false} (done in the prod profile).
 */
@Configuration
public class OpenApiConfig {

    /** Name of the bearer-token security scheme referenced by protected controllers. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI identityOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Identity Service API")
                        .version("v1")
                        .description("Users, roles, authentication (password + optional MFA), refresh-token sessions "
                                + "and the login audit trail. Call it through the API Gateway."))
                // "/" is resolved against the page that loaded the document, i.e. the Gateway.
                .servers(List.of(new Server().url("/").description("API Gateway")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/v1/auth/login (or /auth/mfa/verify). "
                                + "Paste the token only, without the 'Bearer ' prefix.")));
    }

    /**
     * The API document carries no data, so it is reachable without a token (the Gateway fetches it anonymously).
     * Added here so it works whatever {@code bank.security.public-paths} the Config Server supplies.
     */
    @Bean
    @ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", matchIfMissing = true)
    WebSecurityCustomizer publicApiDocs() {
        return web -> web.ignoring().requestMatchers("/v3/api-docs", "/v3/api-docs/**");
    }
}
