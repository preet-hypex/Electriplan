package com.hypex.planna.users;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.hypex.planna.security.SupabaseSettings;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

@Component
class SupabaseAdminApi {

    private final RestClient http;

    SupabaseAdminApi(SupabaseSettings settings, RestClient.Builder builder) {
        this.http = builder
                .baseUrl(settings.authUrl() + "/admin")
                .defaultHeader("apikey", settings.secretKey())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.secretKey())
                .build();
    }

    List<AdminUser> page(int page, int perPage) {
        AdminUser.Page body = http.get()
                .uri("/users?page={page}&per_page={perPage}", page, perPage)
                .exchange((request, response) -> {
                    refuseErrors(response, "list its users");
                    return response.bodyTo(AdminUser.Page.class);
                });
        return body == null || body.users() == null ? List.of() : body.users();
    }

    Optional<AdminUser> find(UUID id) {
        return http.get()
                .uri("/users/{id}", id)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return Optional.empty();
                    }
                    refuseErrors(response, "read user " + id);
                    return Optional.ofNullable(response.bodyTo(AdminUser.class));
                });
    }

    private static void refuseErrors(ConvertibleClientHttpResponse response, String what) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.isError()) {
            throw new IllegalStateException("Supabase Auth would not " + what + ": " + message(errorBody(response), status));
        }
    }

    private static @Nullable Map<?, ?> errorBody(ConvertibleClientHttpResponse response) {
        try {
            return response.bodyTo(Map.class);
        } catch (RestClientException e) {
            return null;
        }
    }

    private static String message(@Nullable Map<?, ?> body, HttpStatusCode status) {
        if (body != null) {
            for (String key : new String[] { "msg", "message", "error_description", "error" }) {
                if (body.get(key) instanceof String text && !text.isBlank()) {
                    return text + " (HTTP " + status.value() + ")";
                }
            }
        }
        return "HTTP " + status.value();
    }
}
