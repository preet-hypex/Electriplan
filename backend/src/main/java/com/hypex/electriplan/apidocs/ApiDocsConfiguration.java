package com.hypex.electriplan.apidocs;

import java.util.List;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApiDocsConfiguration {

    static final String SIGN_IN = "supabase";

    static {
        // Enums as named schemas (MemberRole, Permission, ...), so the web app's
        // generated types have one name for each instead of repeated unions.
        ModelResolver.enumsAsRef = true;
    }

    @Bean
    OpenAPI electriplanOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Electriplan API")
                        .version("v1")
                        .description("""
                                The Electriplan web app's API. Every endpoint needs a Supabase access token \
                                (`Authorization: Bearer ...`). Endpoints that work inside one company take \
                                the `X-Organisation-Id` header and say which permission they need; errors \
                                come back as `{"message": "..."}`.

                                The floor-plan analyser (`/api/floorplan/...`) is a separate service and is \
                                not described here."""))
                .servers(List.of(new Server().url("/").description("The web app's origin (it proxies /api to this service)")))
                .components(new Components().addSecuritySchemes(SIGN_IN, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("A Supabase access token for the signed-in user.")))
                .addSecurityItem(new SecurityRequirement().addList(SIGN_IN));
    }

    /** Every endpoint answers 401 without a valid sign-in. */
    @Bean
    OpenApiCustomizer signInResponses() {
        return api -> api.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                operation.getResponses().addApiResponse("401",
                        new ApiResponse().description("Not signed in, or the access token is invalid or expired."))));
    }
}
