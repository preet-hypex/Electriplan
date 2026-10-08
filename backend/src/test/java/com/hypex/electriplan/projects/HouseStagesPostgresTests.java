package com.hypex.electriplan.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypex.electriplan.PostgresApplicationTest;
import com.hypex.electriplan.TestDatabase;
import com.hypex.electriplan.files.service.InMemoryFileStore;
import com.hypex.electriplan.projects.domain.AnalysisFailedException;
import com.hypex.electriplan.projects.service.FloorPlanAnalyser;
import com.hypex.electriplan.users.service.SupabaseUsers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A house's stage follows its floor-plan work (P7), against Postgres: every
 * move goes along plan_stage_transition (the database refuses others) and is
 * in the house's history with who made it.
 */
class HouseStagesPostgresTests extends PostgresApplicationTest {

    static final UUID A = UUID.fromString("f7000000-0000-4000-8000-0000000000a1");
    static final UUID BUILDER = UUID.fromString("f7000000-0000-4000-8000-000000000001");
    static final UUID SPARKY = UUID.fromString("f7000000-0000-4000-8000-000000000002");

    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @TestConfiguration
    static class Storage {
        @Bean
        @Primary
        InMemoryFileStore inMemoryFileStore() {
            return new InMemoryFileStore();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;
    @MockitoBean FloorPlanAnalyser analyser;

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password());
    }

    @BeforeAll
    static void company(@Autowired javax.sql.DataSource migratedByNow) throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("""
                    INSERT INTO electriplan.organisation (id, name, slug, status, seat_limit) VALUES ('%1$s', 'Stages A', 'stages-a-p7', 'active', 10);
                    INSERT INTO electriplan.supabase_user (id, email, user_metadata, created_at) VALUES
                      ('%2$s', 'builder@p7.com', '{"full_name": "Bea Builder"}', now()), ('%3$s', 'sparky@p7.com', '{}', now());
                    INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES
                      ('%1$s', '%2$s', 'owner'), ('%1$s', '%3$s', 'electrician');
                    """.formatted(A, BUILDER, SPARKY));
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM electriplan.plan_stage_event WHERE organisation_id = '%s'".formatted(A));
            s.execute("DELETE FROM electriplan.organisation WHERE id = '%s'".formatted(A));
            s.execute("DELETE FROM electriplan.supabase_user WHERE id IN ('%s', '%s')".formatted(BUILDER, SPARKY));
        }
    }

    @BeforeEach
    void analyserReadsAnyImage() {
        given(analyser.analyse(any(), anyString(), anyString(), anyString(), any(), anyString())).willAnswer(call -> {
            ObjectNode plan = (ObjectNode) FloorPlansPostgresTests.example("valid/analysed-two-bedroom-unit.json");
            ((ObjectNode) plan.path("source")).put("imageUrl", (String) call.getArgument(3));
            return plan;
        });
    }

    // ---- helpers ----

    private ResultActions call(UUID person, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("X-Organisation-Id", A.toString())
                .with(jwt().jwt(j -> j.subject(person.toString()).tokenValue("t"))));
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String newHouse() throws Exception {
        ObjectNode project = JSON.createObjectNode().put("name", "Stages " + UUID.randomUUID());
        project.putObject("site").put("state", "VIC");
        String projectId = body(call(BUILDER, post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(project.toString()))
                .andExpect(status().isCreated())).path("id").asText();
        return body(call(BUILDER, post("/api/projects/" + projectId + "/houses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Type A\"}")).andExpect(status().isCreated())).path("id").asText();
    }

    private String stage(String houseId) throws Exception {
        return body(call(BUILDER, get("/api/houses/" + houseId))).path("stage").asText();
    }

    /** The moves in the house's history, oldest first: "from>to". */
    private List<String> moves(String houseId) throws Exception {
        JsonNode history = body(call(BUILDER, get("/api/houses/" + houseId + "/stages")).andExpect(status().isOk()));
        List<String> out = new ArrayList<>();
        history.forEach(e -> out.add(0, (e.path("from").isNull() ? "-" : e.path("from").asText()) + ">" + e.path("to").asText()));
        return out;
    }

    private ResultActions saveDraft(UUID person, String houseId, Integer version) throws Exception {
        ObjectNode form = JSON.createObjectNode();
        form.set("document", FloorPlansPostgresTests.example("valid/hand-built-studio.json"));
        if (version != null) {
            form.put("version", version);
        }
        return call(person, put("/api/houses/" + houseId + "/floor-plan/draft").contentType(MediaType.APPLICATION_JSON).content(form.toString()));
    }

    private ResultActions upload(String houseId, Integer version) throws Exception {
        var request = multipart("/api/houses/" + houseId + "/floor-plan/uploads")
                .file(new MockMultipartFile("file", "plan.png", "image/png", HouseUploadsPostgresTests.png(UUID.randomUUID().hashCode() & 0xff)));
        if (version != null) {
            request.param("version", String.valueOf(version));
        }
        return call(BUILDER, request);
    }

    private ResultActions approve(UUID person, String houseId, Integer version) throws Exception {
        String form = version == null ? "{\"note\":\"Checked\"}" : "{\"version\":" + version + ",\"note\":\"Checked\"}";
        return call(person, post("/api/houses/" + houseId + "/floor-plan/approve").contentType(MediaType.APPLICATION_JSON).content(form));
    }

    // ---- tests ----

    @Test
    void aPlanDrawnByHandIsReadyForChecking() throws Exception {
        String houseId = newHouse();
        JsonNode saved = body(saveDraft(BUILDER, houseId, null).andExpect(status().isOk()));
        assertThat(saved.path("houseStage").asText()).isEqualTo("floor_plan_review");
        assertThat(moves(houseId)).containsExactly("->awaiting_upload", "awaiting_upload>floor_plan_review");

        saveDraft(BUILDER, houseId, 0).andExpect(status().isOk());
        assertThat(moves(houseId)).as("editing a plan being checked moves nothing").hasSize(2);
    }

    @Test
    void anUploadedPlanIsAnalysedThenReadyForChecking() throws Exception {
        String houseId = newHouse();
        JsonNode doc = body(upload(houseId, null).andExpect(status().isOk()));
        assertThat(doc.path("houseStage").asText()).isEqualTo("floor_plan_review");
        assertThat(moves(houseId)).containsExactly("->awaiting_upload", "awaiting_upload>analysing", "analysing>floor_plan_review");

        upload(houseId, doc.path("version").asInt()).andExpect(status().isOk());
        assertThat(moves(houseId)).as("a new image re-analyses").endsWith("floor_plan_review>analysing", "analysing>floor_plan_review");
    }

    @Test
    void aFailedAnalysisGoesBackToWhereThePlanStood() throws Exception {
        given(analyser.analyse(any(), anyString(), anyString(), anyString(), any(), anyString()))
                .willThrow(new AnalysisFailedException("No walls were found in the image."));

        String fresh = newHouse();
        upload(fresh, null).andExpect(status().isUnprocessableEntity());
        assertThat(stage(fresh)).isEqualTo("awaiting_upload");
        assertThat(moves(fresh)).containsExactly("->awaiting_upload", "awaiting_upload>analysing", "analysing>awaiting_upload");

        String withPlan = newHouse();
        saveDraft(BUILDER, withPlan, null).andExpect(status().isOk());
        upload(withPlan, 0).andExpect(status().isUnprocessableEntity());
        assertThat(stage(withPlan)).as("it still has its plan").isEqualTo("floor_plan_review");
    }

    @Test
    void approvingSavesAVersionAndEditingAgainReopensIt() throws Exception {
        String houseId = newHouse();
        saveDraft(BUILDER, houseId, null).andExpect(status().isOk());

        JsonNode approved = body(approve(SPARKY, houseId, 0).andExpect(status().isOk()));
        assertThat(approved.path("houseStage").asText()).isEqualTo("floor_plan_approved");
        assertThat(approved.path("state").asText()).isEqualTo("committed");
        assertThat(approved.path("versionNo").asInt()).isEqualTo(1);
        JsonNode history = body(call(BUILDER, get("/api/houses/" + houseId + "/floor-plan/versions")));
        assertThat(history.get(0).path("note").asText()).isEqualTo("Checked");

        approve(SPARKY, houseId, null).andExpect(status().isOk()); // already approved: nothing to do

        JsonNode reopened = body(saveDraft(BUILDER, houseId, null).andExpect(status().isOk()));
        assertThat(reopened.path("houseStage").asText()).isEqualTo("floor_plan_review");
        assertThat(moves(houseId)).containsExactly("->awaiting_upload", "awaiting_upload>floor_plan_review",
                "floor_plan_review>floor_plan_approved", "floor_plan_approved>floor_plan_review");
    }

    @Test
    void onlyAPlanBeingCheckedCanBeApproved() throws Exception {
        String houseId = newHouse();
        approve(BUILDER, houseId, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("There is no floor plan to approve yet."));
        saveDraft(BUILDER, houseId, null).andExpect(status().isOk());
        approve(BUILDER, houseId, 7).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(Matchers.startsWith("Someone changed this")));
        assertThat(stage(houseId)).isEqualTo("floor_plan_review");
    }

    @Test
    void theHistorySaysWhoMovedTheStage() throws Exception {
        String houseId = newHouse();
        saveDraft(BUILDER, houseId, null).andExpect(status().isOk());
        approve(SPARKY, houseId, 0).andExpect(status().isOk());

        JsonNode history = body(call(SPARKY, get("/api/houses/" + houseId + "/stages")).andExpect(status().isOk()));
        assertThat(history).hasSize(3);
        JsonNode latest = history.get(0);
        assertThat(latest.path("to").asText()).isEqualTo("floor_plan_approved");
        assertThat(latest.path("by").asText()).isEqualTo(SPARKY.toString());
        assertThat(latest.path("byName").asText()).as("no name given: the email").isEqualTo("sparky@p7.com");
        assertThat(history.get(1).path("byName").asText()).isEqualTo("Bea Builder");
        assertThat(latest.path("at").asText()).isNotBlank();
    }

    @Test
    void aHouseInReviewOrLaterIsNotMovedByFloorPlanEdits() throws Exception {
        String houseId = newHouse();
        saveDraft(BUILDER, houseId, null).andExpect(status().isOk());
        approve(BUILDER, houseId, 0).andExpect(status().isOk());
        try (Connection c = owner(); Statement s = c.createStatement()) {
            // Electrical design onwards is not built yet: put the house there as later stories will.
            s.execute("UPDATE electriplan.plan SET stage = 'electrical_design' WHERE id = '" + houseId + "'");
            s.execute("UPDATE electriplan.plan SET stage = 'electrical_review' WHERE id = '" + houseId + "'");
        }
        saveDraft(BUILDER, houseId, null).andExpect(status().isOk());
        assertThat(stage(houseId)).isEqualTo("electrical_review");
    }
}
