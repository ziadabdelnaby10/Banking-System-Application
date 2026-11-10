package org.bank.customer.config;

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
 * OpenAPI description of the Customer service. The document is served at {@code /v3/api-docs}; the Swagger UI
 * is hosted by the API Gateway, which fetches it through {@code /docs/customer/v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    /** Name of the bearer-token security scheme referenced by protected controllers. */
    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI customerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Customer Service API")
                        .version("v1")
                        .description("Branches, customers and KYC. Tokens are issued by the Identity service; "
                                + "call it through the API Gateway."))
                .servers(List.of(new Server().url("/").description("API Gateway")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from the Identity service (POST /api/v1/auth/login). "
                                + "Paste the token only, without the 'Bearer ' prefix.")));
    }

    /** The API document carries no data, so the Gateway may fetch it anonymously. */
    @Bean
    @ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", matchIfMissing = true)
    WebSecurityCustomizer publicApiDocs() {
        return web -> web.ignoring().requestMatchers("/v3/api-docs", "/v3/api-docs/**");
    }
}
