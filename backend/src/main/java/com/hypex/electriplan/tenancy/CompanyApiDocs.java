package com.hypex.electriplan.tenancy;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Documents company-scoped endpoints as the interceptor enforces them: the
 * {@code X-Organisation-Id} header, the 400 and 403 answers, and the
 * permission needed with the roles that have it (also as the extensions
 * {@code x-permission} and {@code x-roles}, for tools).
 */
@Component
class CompanyApiDocs implements OperationCustomizer, OpenApiCustomizer {

    static final String ERROR = "ErrorMessage";

    @Override
    public Operation customize(Operation operation, HandlerMethod method) {
        if (!CompanyContextInterceptor.isCompanyScoped(method)) {
            return operation;
        }
        operation.addParametersItem(new HeaderParameter()
                .name(CompanyResolver.HEADER)
                .required(false)
                .schema(new StringSchema().format("uuid"))
                .description("The company to act in. Needed only when the caller belongs to more than one."));

        Permission needed = CompanyContextInterceptor.requiredPermission(method);
        String forbidden = "The caller is not an active member of that company, or it is closed.";
        if (needed != null) {
            String roles = PermissionMatrix.rolesWith(needed).stream().map(MemberRole::code).collect(Collectors.joining(", "));
            forbidden = forbidden + " Or the caller's role there lacks `" + needed.code() + "`.";
            String rule = "**Permission:** `" + needed.code() + "` (roles: " + roles + ").";
            operation.description(operation.getDescription() == null ? rule : operation.getDescription() + "\n\n" + rule);
            operation.addExtension("x-permission", needed.code());
            operation.addExtension("x-roles", PermissionMatrix.rolesWith(needed).stream().map(MemberRole::code).toList());
        }
        operation.getResponses()
                .addApiResponse("400", error("No company chosen while the caller belongs to several, or the header is not a company id."))
                .addApiResponse("403", error(forbidden));
        return operation;
    }

    /** The {"message": "..."} body of every refusal (TenancyErrors). */
    @Override
    public void customise(io.swagger.v3.oas.models.OpenAPI api) {
        Schema<?> message = new StringSchema().description("What went wrong, in words to show the user.");
        api.getComponents().addSchemas(ERROR, new ObjectSchema()
                .addProperty("message", message)
                .required(List.of("message")));
    }

    private static ApiResponse error(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR))));
    }
}
