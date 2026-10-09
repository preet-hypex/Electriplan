package com.hypex.electriplan.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypex.electriplan.PostgresApplicationTest;
import com.hypex.electriplan.TestDatabase;
import com.hypex.electriplan.users.service.SupabaseUsers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A house's floor plan end to end (P4), against Postgres as the API's own
 * login: the draft saved as the editor goes, versions frozen from it (the
 * database refuses to change them), history and restore, and who may do what.
 */
class FloorPlansPostgresTests extends PostgresApplicationTest {

    static final UUID A = UUID.fromString("f4000000-0000-4000-8000-0000000000a1");
    static final UUID B = UUID.fromString("f4000000-0000-4000-8000-0000000000b1");
    static final UUID BUILDER = UUID.fromString("f4000000-0000-4000-8000-000000000001");
    static final UUID ELECTRICIAN = UUID.fromString("f4000000-0000-4000-8000-000000000002");
    static final UUID VIEWER = UUID.fromString("f4000000-0000-4000-8000-000000000003");
    static final UUID B_OWNER = UUID.fromString("f4000000-0000-4000-8000-000000000004");
    static final UUID OWNER = UUID.fromString("f4000000-0000-4000-8000-000000000005");

    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password());
    }

    @BeforeAll
    static void companies(@Autowired javax.sql.DataSource migratedByNow) throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("""
                    INSERT INTO electriplan.organisation (id, name, slug, status, seat_limit) VALUES
                      ('%1$s', 'Plans A', 'plans-a-p4', 'active', 10), ('%2$s', 'Plans B', 'plans-b-p4', 'active', 10);
                    INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES
                      ('%3$s', 'builder@p4.com', now()), ('%4$s', 'sparky@p4.com', now()), ('%5$s', 'viewer@p4.com', now()),
                      ('%6$s', 'owner@p4b.com', now()), ('%7$s', 'owner@p4.com', now());
                    INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES
                      ('%1$s', '%7$s', 'owner'), ('%1$s', '%3$s', 'builder'), ('%1$s', '%4$s', 'electrician'),
                      ('%1$s', '%5$s', 'viewer'), ('%2$s', '%6$s', 'owner');
                    """.formatted(A, B, BUILDER, ELECTRICIAN, VIEWER, B_OWNER, OWNER));
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM electriplan.plan_stage_event WHERE organisation_id IN ('%s', '%s')".formatted(A, B));
            s.execute("DELETE FROM electriplan.organisation WHERE id IN ('%s', '%s')".formatted(A, B));
            s.execute("DELETE FROM electriplan.supabase_user WHERE id IN ('%s', '%s', '%s', '%s', '%s')"
                    .formatted(BUILDER, ELECTRICIAN, VIEWER, B_OWNER, OWNER));
        }
    }

    // ---- helpers ----

    private ResultActions call(UUID person, MockHttpServletRequestBuilder request) throws Exception {
        UUID company = person.equals(B_OWNER) ? B : A;
        return mvc.perform(request.header("X-Organisation-Id", company.toString())
                .with(jwt().jwt(j -> j.subject(person.toString())))
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    static JsonNode example(String name) throws IOException {
        try (InputStream in = FloorPlansPostgresTests.class.getClassLoader()
                .getResourceAsStream("contracts/examples/floor-plan/" + name)) {
            return JSON.readTree(in);
        }
    }

    /** A new house in a new project; returns the house's floor-plan URL. */
    private String newHouse(UUID person) throws Exception {
        ObjectNode project = JSON.createObjectNode().put("name", "Plans " + UUID.randomUUID());
        project.putObject("site").put("state", "VIC");
        String projectId = body(call(person, post("/api/projects").content(project.toString())).andExpect(status().isCreated()))
                .path("id").asText();
        String houseId = body(call(person, post("/api/projects/" + projectId + "/houses").content("{\"name\":\"Type A\"}"))
                .andExpect(status().isCreated())).path("id").asText();
        return "/api/houses/" + houseId + "/floor-plan";
    }

    private static String draft(JsonNode plan, Integer version) {
        ObjectNode form = JSON.createObjectNode();
        form.set("document", plan);
        if (version != null) {
            form.put("version", version);
        }
        return form.toString();
    }

    private static String commit(int version, String note) {
        return JSON.createObjectNode().put("version", version).put("note", note).toString();
    }

    // ---- tests ----

    @Test
    void aNewHouseHasNoFloorPlanYet() throws Exception {
        String url = newHouse(BUILDER);
        call(VIEWER, get(url)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This house has no floor plan yet."));
        assertThat(body(call(VIEWER, get(url + "/versions")).andExpect(status().isOk()))).isEmpty();
    }

    @Test
    void theEditorsPlanIsKeptAsTheDraftAndOpensWhereItWasLeft() throws Exception {
        String url = newHouse(BUILDER);
        JsonNode plan = example("valid/analysed-two-bedroom-unit.json");

        JsonNode saved = body(call(BUILDER, put(url + "/draft").content(draft(plan, null))).andExpect(status().isOk()));
        assertThat(saved.path("state").asText()).isEqualTo("draft");
        assertThat(saved.path("versionNo").asInt()).isEqualTo(1);
        assertThat(saved.path("version").asInt()).isZero();
        assertThat(saved.path("savedBy").asText()).isEqualTo(BUILDER.toString());
        assertThat(saved.path("document").path("rooms")).hasSize(4);
        assertThat(saved.path("unsavedChanges").asBoolean()).as("a draft from nothing is unsaved work").isTrue();

        JsonNode opened = body(call(VIEWER, get(url)).andExpect(status().isOk()));
        assertThat(opened.path("document")).isEqualTo(saved.path("document"));
        assertThat(opened.path("versionNo").asInt()).isEqualTo(1);

        // Autosave again: the same draft, a newer version of it.
        ObjectNode edited = plan.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) edited.path("rooms")).remove(0);
        JsonNode again = body(call(ELECTRICIAN, put(url + "/draft").content(draft(edited, 0))).andExpect(status().isOk()));
        assertThat(again.path("versionNo").asInt()).isEqualTo(1);
        assertThat(again.path("version").asInt()).isEqualTo(1);
        assertThat(body(call(VIEWER, get(url))).path("document").path("rooms")).hasSize(3);
    }

    @Test
    void aStaleSaveIsRefusedNotMerged() throws Exception {
        String url = newHouse(BUILDER);
        JsonNode plan = example("valid/hand-built-studio.json");
        call(BUILDER, put(url + "/draft").content(draft(plan, null))).andExpect(status().isOk());
        call(ELECTRICIAN, put(url + "/draft").content(draft(plan, 0))).andExpect(status().isOk());

        call(BUILDER, put(url + "/draft").content(draft(plan, 0))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.startsWith("Someone changed this since you opened it")));
        call(BUILDER, put(url + "/draft").content(draft(plan, null))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    void anInvalidPlanIsRefusedWithItsProblems() throws Exception {
        String url = newHouse(BUILDER);
        JsonNode problem = body(call(BUILDER, put(url + "/draft").content(draft(example("invalid/units-in-feet.json"), null)))
                .andExpect(status().isBadRequest()));
        assertThat(problem.path("errors").get(0).path("field").asText()).isEqualTo("document");
        assertThat(problem.path("errors").toString()).contains("units");
        call(BUILDER, put(url + "/draft").content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Send the floor plan"));
    }

    @Test
    void savingAVersionFreezesItAndTheNextEditStartsANewDraft() throws Exception {
        String url = newHouse(BUILDER);
        JsonNode plan = example("valid/analysed-two-bedroom-unit.json");
        call(BUILDER, put(url + "/draft").content(draft(plan, null))).andExpect(status().isOk());

        JsonNode v1 = body(call(BUILDER, post(url + "/versions").content(commit(0, " First check done ")))
                .andExpect(status().isCreated()));
        assertThat(v1.path("versionNo").asInt()).isEqualTo(1);
        assertThat(v1.path("state").asText()).isEqualTo("committed");
        assertThat(v1.path("current").asBoolean()).isTrue();
        assertThat(v1.path("note").asText()).isEqualTo("First check done");
        assertThat(v1.path("rooms").asInt()).isEqualTo(4);
        assertThat(v1.path("walls").asInt()).isEqualTo(6);
        assertThat(v1.path("openings").asInt()).isEqualTo(5);
        assertThat(v1.path("floorAreaM2").decimalValue()).isPositive();
        assertThat(v1.path("committedAt").asText()).isNotBlank();

        JsonNode opened = body(call(VIEWER, get(url)));
        assertThat(opened.path("state").asText()).as("with no draft, the newest version opens").isEqualTo("committed");

        call(BUILDER, post(url + "/versions").content(commit(1, "again"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("There are no unsaved changes to save as a version."));
        call(BUILDER, put(url + "/draft").content(draft(plan, 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("saved as a version since you opened it")));

        JsonNode v2draft = body(call(BUILDER, put(url + "/draft").content(draft(plan, null))).andExpect(status().isOk()));
        assertThat(v2draft.path("versionNo").asInt()).isEqualTo(2);
        assertThat(v2draft.path("basedOnVersionNo").asInt()).isEqualTo(1);

        JsonNode history = body(call(VIEWER, get(url + "/versions")).andExpect(status().isOk()));
        assertThat(history).hasSize(2);
        assertThat(history.get(0).path("versionNo").asInt()).isEqualTo(2);
        assertThat(history.get(0).path("state").asText()).isEqualTo("draft");
        assertThat(history.get(0).path("current").asBoolean()).isFalse();
        assertThat(history.get(1).path("current").asBoolean()).isTrue();

        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("BEGIN");
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> s.execute(
                    "UPDATE electriplan.floor_plan_version SET note = 'rewritten' WHERE state = 'committed' AND organisation_id = '" + A + "'")))
                    .as("the database refuses to change a committed version").hasMessageContaining("cannot be changed");
            s.execute("ROLLBACK");
        }
    }

    @Test
    void anEarlierVersionCanBeLookedAtAndRestored() throws Exception {
        String url = newHouse(BUILDER);
        JsonNode first = example("valid/hand-built-studio.json");
        JsonNode second = example("valid/analysed-two-bedroom-unit.json");
        call(BUILDER, put(url + "/draft").content(draft(first, null))).andExpect(status().isOk());
        call(BUILDER, post(url + "/versions").content(commit(0, "studio"))).andExpect(status().isCreated());
        call(BUILDER, put(url + "/draft").content(draft(second, null))).andExpect(status().isOk());

        JsonNode v1 = body(call(VIEWER, get(url + "/versions/1")).andExpect(status().isOk()));
        assertThat(v1.path("document").path("rooms")).hasSize(2);
        call(VIEWER, get(url + "/versions/9")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("This house has no floor-plan version 9."));

        call(BUILDER, post(url + "/versions/1/restore").content("{\"version\":5}")).andExpect(status().isConflict());

        // The draft (the two-bedroom plan) was never saved as a version: it is kept first.
        JsonNode restored = body(call(BUILDER, post(url + "/versions/1/restore").content("{\"version\":0}")).andExpect(status().isOk()));
        assertThat(restored.path("keptAsVersionNo").asInt()).isEqualTo(2);
        JsonNode draft = restored.path("draft");
        assertThat(draft.path("state").asText()).isEqualTo("draft");
        assertThat(draft.path("versionNo").asInt()).isEqualTo(3);
        assertThat(draft.path("basedOnVersionNo").asInt()).isEqualTo(1);
        assertThat(draft.path("document").path("rooms")).hasSize(2);

        JsonNode history = body(call(VIEWER, get(url + "/versions")));
        assertThat(history.get(1).path("versionNo").asInt()).isEqualTo(2);
        assertThat(history.get(1).path("state").asText()).isEqualTo("committed");
        assertThat(history.get(1).path("note").asText()).isEqualTo("Before restoring version 1");
        assertThat(history.get(1).path("rooms").asInt()).as("the work that was in the draft").isEqualTo(4);
        assertThat(body(call(VIEWER, get(url + "/versions/2"))).path("document").path("rooms")).hasSize(4);

        assertThat(draft.path("unsavedChanges").asBoolean()).as("a copy of version 1, unchanged").isFalse();

        // The draft is now an unchanged copy of version 1: restoring again keeps nothing more.
        JsonNode again = body(call(BUILDER, post(url + "/versions/2/restore").content("{\"version\":" + draft.path("version").asInt() + "}"))
                .andExpect(status().isOk()));
        assertThat(again.path("keptAsVersionNo").isNull()).isTrue();
        assertThat(again.path("draft").path("versionNo").asInt()).isEqualTo(3);
        assertThat(again.path("draft").path("document").path("rooms")).hasSize(4);
        assertThat(body(call(VIEWER, get(url + "/versions")))).hasSize(3);

        call(BUILDER, post(url + "/versions/3/restore").content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Version 3 is the draft already."));
    }

    @Test
    void theDraftsChangesCanBeLetGoOnRestore() throws Exception {
        String url = newHouse(BUILDER);
        call(BUILDER, put(url + "/draft").content(draft(example("valid/hand-built-studio.json"), null))).andExpect(status().isOk());
        call(BUILDER, post(url + "/versions").content(commit(0, "studio"))).andExpect(status().isCreated());
        JsonNode edited = body(call(BUILDER, put(url + "/draft").content(draft(example("valid/analysed-two-bedroom-unit.json"), null))));
        assertThat(edited.path("unsavedChanges").asBoolean()).isTrue();

        JsonNode restored = body(call(BUILDER, post(url + "/versions/1/restore").content("{\"version\":0,\"keepDraft\":false}"))
                .andExpect(status().isOk()));
        assertThat(restored.path("keptAsVersionNo").isNull()).isTrue();
        assertThat(restored.path("draft").path("versionNo").asInt()).as("the same draft, now version 1's contents").isEqualTo(2);
        assertThat(restored.path("draft").path("unsavedChanges").asBoolean()).isFalse();
        assertThat(body(call(VIEWER, get(url + "/versions")))).hasSize(2);
    }

    @Test
    void restoringWithNoDraftKeepsNothing() throws Exception {
        String url = newHouse(BUILDER);
        call(BUILDER, put(url + "/draft").content(draft(example("valid/hand-built-studio.json"), null))).andExpect(status().isOk());
        call(BUILDER, post(url + "/versions").content(commit(0, "studio"))).andExpect(status().isCreated());

        JsonNode restored = body(call(BUILDER, post(url + "/versions/1/restore").content("{}")).andExpect(status().isOk()));
        assertThat(restored.path("keptAsVersionNo").isNull()).isTrue();
        assertThat(restored.path("draft").path("versionNo").asInt()).isEqualTo(2);
    }

    @Test
    void savingIsActivityOnTheHouse() throws Exception {
        String url = newHouse(BUILDER);
        String houseId = url.split("/")[3];
        call(BUILDER, put(url + "/draft").content(draft(example("valid/empty-plan.json"), null))).andExpect(status().isOk());

        JsonNode recent = body(call(VIEWER, get("/api/houses/recent").param("size", "1")));
        assertThat(recent.get(0).path("id").asText()).isEqualTo(houseId);

        try (Connection c = owner(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT h.updated_at >= v.updated_at FROM electriplan.plan h "
                     + "JOIN electriplan.plan_level l ON l.plan_id = h.id JOIN electriplan.floor_plan_version v ON v.plan_level_id = l.id "
                     + "WHERE h.id = '" + houseId + "'")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getBoolean(1)).isTrue();
        }
    }

    @Test
    void everyoneMayLookOnlyFloorPlanEditorsMaySave() throws Exception {
        String url = newHouse(OWNER);
        JsonNode plan = example("valid/empty-plan.json");
        call(VIEWER, put(url + "/draft").content(draft(plan, null))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("floor-plan.edit")));
        call(ELECTRICIAN, put(url + "/draft").content(draft(plan, null))).andExpect(status().isOk());
        call(VIEWER, get(url)).andExpect(status().isOk());
        call(VIEWER, post(url + "/versions").content(commit(0, "x"))).andExpect(status().isForbidden());
    }

    @Test
    void anArchivedHousesPlanIsReadOnly() throws Exception {
        String url = newHouse(BUILDER);
        String houseId = url.split("/")[3];
        call(BUILDER, put(url + "/draft").content(draft(example("valid/empty-plan.json"), null))).andExpect(status().isOk());
        call(BUILDER, post("/api/houses/" + houseId + "/archive")).andExpect(status().isOk());

        call(BUILDER, put(url + "/draft").content(draft(example("valid/empty-plan.json"), 0))).andExpect(status().isConflict());
        call(VIEWER, get(url)).andExpect(status().isOk());
    }

    @Test
    void anotherCompanysFloorPlanDoesNotExist() throws Exception {
        String url = newHouse(BUILDER);
        call(BUILDER, put(url + "/draft").content(draft(example("valid/empty-plan.json"), null))).andExpect(status().isOk());

        call(B_OWNER, get(url)).andExpect(status().isNotFound());
        call(B_OWNER, get(url + "/versions")).andExpect(status().isNotFound());
        call(B_OWNER, put(url + "/draft").content(draft(example("valid/empty-plan.json"), 0))).andExpect(status().isNotFound());
    }
}
