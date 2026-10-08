package com.hypex.electriplan.apidocs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypex.electriplan.users.SupabaseUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** What the OpenAPI document says, beyond what springdoc works out by itself. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiDocsTests {

    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;

    JsonNode api;

    @BeforeEach
    void load() throws Exception {
        api = new ObjectMapper().readTree(mvc.perform(get("/api/docs/openapi.json"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode op(String path) {
        return api.path("paths").path(path).path("get");
    }

    private JsonNode schema(String name) {
        return api.path("components").path("schemas").path(name);
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.isObject() ? n.path("name").asText() : n.asText()));
        return out;
    }

    @Test
    void theDocumentAndSwaggerUiNeedNoSignIn() throws Exception {
        String ui = mvc.perform(get("/api/docs"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(ui).endsWith("/api/swagger-ui/index.html");
        mvc.perform(get("/api/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/api/docs/openapi.json/swagger-config"))
                .andExpect(status().isOk());
    }

    @Test
    void describesOnlyTheApiWithASameOriginServer() {
        assertThat(api.path("openapi").asText()).startsWith("3.0");
        assertThat(api.path("info").path("title").asText()).isEqualTo("Electriplan API");
        assertThat(api.path("servers").get(0).path("url").asText()).isEqualTo("/");
        api.path("paths").fieldNames().forEachRemaining(path -> assertThat(path).startsWith("/api/"));
        assertThat(api.path("paths").has("/api/docs/openapi.json")).isFalse();
    }

    @Test
    void everyEndpointNeedsTheSupabaseTokenAndSaysSo() {
        JsonNode scheme = api.path("components").path("securitySchemes").path("supabase");
        assertThat(scheme.path("type").asText()).isEqualTo("http");
        assertThat(scheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(api.path("security").get(0).has("supabase")).isTrue();
        api.path("paths").forEach(path -> path.forEach(operation ->
                assertThat(operation.path("responses").has("401")).as(operation.path("operationId").asText()).isTrue()));
    }

    @Test
    void operationsHaveStableNames() {
        assertThat(op("/api/me").path("operationId").asText()).isEqualTo("getMe");
        assertThat(op("/api/organisations").path("operationId").asText()).isEqualTo("listMyCompanies");
        assertThat(op("/api/organisations/current").path("operationId").asText()).isEqualTo("getCurrentCompany");
        assertThat(op("/api/organisations/current/licence").path("operationId").asText()).isEqualTo("getCurrentLicence");
        assertThat(op("/api/reference/distributors").path("operationId").asText()).isEqualTo("listDistributors");
    }

    @Test
    void companyScopedEndpointsTakeTheCompanyHeaderAndDocumentRefusals() {
        JsonNode current = op("/api/organisations/current");
        assertThat(texts(current.path("parameters"))).containsExactly("X-Organisation-Id");
        JsonNode header = current.path("parameters").get(0);
        assertThat(header.path("in").asText()).isEqualTo("header");
        assertThat(header.path("required").asBoolean()).isFalse();
        assertThat(header.path("schema").path("format").asText()).isEqualTo("uuid");
        assertThat(current.path("responses").has("400")).isTrue();
        assertThat(current.path("responses").path("403").path("content").path("application/json").path("schema")
                .path("$ref").asText()).isEqualTo("#/components/schemas/ErrorMessage");
        assertThat(current.has("x-permission")).isFalse();
        assertThat(texts(schema("ErrorMessage").path("required"))).containsExactly("message");
    }

    @Test
    void endpointsOutsideACompanyDoNot() {
        for (String path : List.of("/api/me", "/api/organisations", "/api/reference/distributors")) {
            JsonNode operation = op(path);
            assertThat(texts(operation.path("parameters"))).as(path).doesNotContain("X-Organisation-Id");
            assertThat(operation.path("responses").has("403")).as(path).isFalse();
        }
    }

    @Test
    void permissionsAreDocumentedFromTheMatrix() {
        JsonNode licence = op("/api/organisations/current/licence");
        assertThat(licence.path("x-permission").asText()).isEqualTo("licence.view");
        assertThat(texts(licence.path("x-roles"))).containsExactly("owner", "admin");
        assertThat(licence.path("description").asText()).contains("`licence.view` (roles: owner, admin)");
        assertThat(licence.path("responses").path("403").path("description").asText()).contains("licence.view");
        assertThat(texts(licence.path("parameters"))).containsExactly("X-Organisation-Id");
    }

    @Test
    void projectEndpointsKeepTheirFieldErrorsAndSayWhoMayCallThem() {
        JsonNode create = api.path("paths").path("/api/projects").path("post");
        assertThat(create.path("x-permission").asText()).isEqualTo("project.edit");
        assertThat(texts(create.path("x-roles"))).containsExactly("owner", "admin", "builder");
        JsonNode invalid = create.path("responses").path("400");
        assertThat(invalid.path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/ValidationProblem");
        assertThat(invalid.path("description").asText()).contains("`errors` names each one").contains("No company chosen");
        assertThat(create.path("responses").has("201")).isTrue();

        JsonNode get = op("/api/projects/{id}");
        assertThat(get.path("x-permission").asText()).isEqualTo("company.view");
        assertThat(get.path("responses").path("200").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/Project");
        assertThat(get.path("responses").path("404").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/ErrorMessage");

        JsonNode form = schema("ProjectForm");
        assertThat(texts(form.path("required"))).as("optional fields are optional in requests").containsExactlyInAnyOrder("name", "site");
        assertThat(texts(schema("ProjectStatus").path("enum"))).containsExactly("active", "on_hold", "completed", "cancelled");
    }

    @Test
    void enumsUseTheirJsonValues() {
        assertThat(texts(schema("MemberRole").path("enum"))).containsExactly("owner", "admin", "builder", "electrician", "viewer");
        assertThat(texts(schema("LicenceStatus").path("enum"))).containsExactly("trial", "active", "suspended", "closed");
        assertThat(texts(schema("Permission").path("enum"))).contains("company.view", "licence.view", "design.sign-off");
        JsonNode current = schema("Current").path("properties");
        assertThat(current.path("role").path("$ref").asText()).isEqualTo("#/components/schemas/MemberRole");
        assertThat(current.path("permissions").path("items").path("$ref").asText()).isEqualTo("#/components/schemas/Permission");
        assertThat(current.path("permissions").path("uniqueItems").asBoolean()).isTrue();
    }

    @Test
    void recordFieldsAreAlwaysPresentAndNullableOnlyWhenMarked() {
        JsonNode licence = schema("Licence");
        assertThat(texts(licence.path("required")))
                .containsExactlyInAnyOrder("status", "seatLimit", "seatsInUse", "licenceStartsOn", "licenceEndsOn");
        assertThat(licence.path("properties").path("licenceEndsOn").path("nullable").asBoolean()).isTrue();
        assertThat(licence.path("properties").path("seatLimit").path("nullable").asBoolean()).isFalse();

        JsonNode me = schema("Me");
        assertThat(texts(me.path("required"))).containsExactlyInAnyOrder("id", "email", "tokenExpiresAt", "copy");
        assertThat(me.path("properties").path("id").path("nullable").asBoolean()).isFalse();
        JsonNode copy = me.path("properties").path("copy");
        assertThat(copy.path("nullable").asBoolean()).isTrue();
        assertThat(copy.path("allOf").get(0).path("$ref").asText()).isEqualTo("#/components/schemas/SupabaseUser");
        assertThat(schema("SupabaseUser").path("properties").path("userMetadata").path("additionalProperties").asBoolean()).isTrue();
    }

    @Test
    void distributorCodesAreStrings() {
        JsonNode code = schema("Distributor").path("properties").path("code");
        JsonNode resolved = code.has("$ref")
                ? schema(code.path("$ref").asText().substring("#/components/schemas/".length()))
                : code;
        assertThat(resolved.path("type").asText()).isEqualTo("string");
        assertThat(texts(schema("Distributor").path("required"))).containsExactlyInAnyOrder("code", "name", "state");
        assertThat(texts(schema("AustralianState").path("enum"))).contains("VIC", "NSW");
    }
}
