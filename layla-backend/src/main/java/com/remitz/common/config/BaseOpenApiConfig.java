package com.remitz.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

import java.util.List;
import java.util.Map;

public class BaseOpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @SuppressWarnings("rawtypes")
    public static OpenAPI createOpenAPI(String title, String description, String version) {
        Schema<?> errorSchema = new Schema<>()
                .type("object")
                .addProperty("error", new Schema<>().type("string").example("Bad Request"))
                .addProperty("message", new Schema<>().type("string").example("Validation failed"))
                .addProperty("details", new Schema<>().type("object"))
                .addProperty("timestamp", new Schema<>().type("string").format("date-time").example("2026-04-13T12:00:00.000Z"))
                .addProperty("path", new Schema<>().type("string").example("/api/auth/login"));

        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description(description)
                        .version(version)
                        .contact(new Contact()
                                .name("Layla Money Transfer Engineering")
                                .email("engineering@laylamoneytransfer.com")
                                .url("https://layla.money"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://layla.money/terms")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://layla.money").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT access token. Obtain one via `POST /api/auth/login`."))
                        .addSchemas("ErrorResponse", errorSchema)
                        .addResponses("BadRequest", new ApiResponse()
                                .description("Bad Request — validation or input error")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))))
                        .addResponses("Unauthorized", new ApiResponse()
                                .description("Unauthorized — missing or invalid JWT token"))
                        .addResponses("Forbidden", new ApiResponse()
                                .description("Forbidden — insufficient permissions for this operation"))
                        .addResponses("NotFound", new ApiResponse()
                                .description("Not Found — resource does not exist"))
                        .addResponses("TooManyRequests", new ApiResponse()
                                .description("Too Many Requests — rate limit exceeded"))
                        .addResponses("InternalError", new ApiResponse()
                                .description("Internal Server Error — unexpected failure")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))))));
    }
}
