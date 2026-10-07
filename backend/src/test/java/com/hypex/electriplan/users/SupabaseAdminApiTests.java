package com.hypex.electriplan.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.hypex.electriplan.security.SupabaseSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SupabaseAdminApiTests {

    private static final String USERS = "https://abc.supabase.co/auth/v1/admin/users";
    private static final UUID ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private MockRestServiceServer server;
    private SupabaseAdminApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new SupabaseAdminApi(new SupabaseSettings("https://abc.supabase.co/", "sb_secret_key"), builder);
    }

    @Test
    void listsUsersWithTheSecretKeyAndReadsSupabasesFields() {
        server.expect(requestTo(USERS + "?page=2&per_page=500"))
                .andExpect(header("apikey", "sb_secret_key"))
                .andExpect(header("Authorization", "Bearer sb_secret_key"))
                .andRespond(withSuccess("""
                        {"aud": "authenticated", "users": [{
                          "id": "%s", "aud": "authenticated", "role": "authenticated",
                          "email": "sam@example.com", "phone": "",
                          "email_confirmed_at": "2026-10-01T09:30:00.123456Z",
                          "last_sign_in_at": "2026-10-05T08:00:00Z",
                          "app_metadata": {"provider": "email", "providers": ["email"]},
                          "user_metadata": {"full_name": "Sam Lee"},
                          "identities": [], "is_anonymous": false,
                          "created_at": "2026-09-30T00:00:00Z", "updated_at": "2026-10-05T08:00:00Z"
                        }]}
                        """.formatted(ID), MediaType.APPLICATION_JSON));

        AdminUser user = api.page(2, 500).getFirst();

        assertThat(user.id()).isEqualTo(ID);
        assertThat(user.email()).isEqualTo("sam@example.com");
        assertThat(user.phone()).as("Supabase sends an empty phone for email accounts").isNull();
        assertThat(user.userMetadata()).isEqualTo(Map.of("full_name", "Sam Lee"));
        assertThat(user.appMetadata()).containsEntry("provider", "email");
        assertThat(user.emailConfirmedAt()).isEqualTo(Instant.parse("2026-10-01T09:30:00.123456Z"));
        assertThat(user.invitedAt()).isNull();
        assertThat(user.createdAt()).isEqualTo(Instant.parse("2026-09-30T00:00:00Z"));
        server.verify();
    }

    @Test
    void aUserSupabaseDoesNotHaveIsEmptyNotAnError() {
        server.expect(requestTo(USERS + "/" + ID)).andRespond(withResourceNotFound()
                .contentType(MediaType.APPLICATION_JSON).body("{\"code\":404,\"msg\":\"User not found\"}"));

        assertThat(api.find(ID)).isEmpty();
    }

    @Test
    void saysWhySupabaseRefused() {
        server.expect(requestTo(USERS + "?page=1&per_page=500")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"message\":\"Invalid API key\"}"));

        assertThatThrownBy(() -> api.page(1, 500)).hasMessage("Supabase Auth would not list its users: Invalid API key (HTTP 401)");
    }
}
