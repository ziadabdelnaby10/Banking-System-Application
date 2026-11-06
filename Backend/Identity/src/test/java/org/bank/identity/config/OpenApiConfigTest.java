package org.bank.identity.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;

import static org.assertj.core.api.Assertions.assertThat;

/** No database needed: checks the OpenAPI description and that docs exposure follows springdoc.api-docs.enabled. */
class OpenApiConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OpenApiConfig.class);

    @Test
    void describesBearerAuthAndGatewayServer() {
        runner.run(context -> {
            OpenAPI api = context.getBean(OpenAPI.class);

            assertThat(api.getInfo().getTitle()).isEqualTo("Identity Service API");
            assertThat(api.getServers()).extracting("url").containsExactly("/");
            SecurityScheme scheme = api.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH);
            assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.HTTP);
            assertThat(scheme.getScheme()).isEqualTo("bearer");
            assertThat(scheme.getBearerFormat()).isEqualTo("JWT");
        });
    }

    @Test
    void docsArePublicByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(WebSecurityCustomizer.class));
    }

    @Test
    void docsAreNotExposedWhenDisabled() {
        runner.withPropertyValues("springdoc.api-docs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WebSecurityCustomizer.class));
    }
}
