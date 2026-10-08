package com.hypex.electriplan.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypex.electriplan.PostgresApplicationTest;
import com.hypex.electriplan.TestDatabase;
import com.hypex.electriplan.files.service.FileStore;
import com.hypex.electriplan.files.service.InMemoryFileStore;
import com.hypex.electriplan.projects.domain.AnalysisFailedException;
import com.hypex.electriplan.projects.service.FloorPlanAnalyser;
import com.hypex.electriplan.users.service.SupabaseUsers;
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
 * Uploading a floor-plan image for a house (P5), against Postgres as the API's
 * own login: the image kept once in the company's files, the analysis run
 * recorded, the plan saved as the draft, and the image served to the
 * company's members only. S3 is in memory and the analyser is a stand-in.
 */
class HouseUploadsPostgresTests extends PostgresApplicationTest {

    static final UUID A = UUID.fromString("f5000000-0000-4000-8000-0000000000a1");
    static final UUID B = UUID.fromString("f5000000-0000-4000-8000-0000000000b1");
    static final UUID BUILDER = UUID.fromString("f5000000-0000-4000-8000-000000000001");
    static final UUID VIEWER = UUID.fromString("f5000000-0000-4000-8000-000000000002");
    static final UUID B_OWNER = UUID.fromString("f5000000-0000-4000-8000-000000000003");

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
    @Autowired FileStore store;
    @MockitoBean SupabaseUsers users;
    @MockitoBean FloorPlanAnalyser analyser;

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password());
    }

    @BeforeAll
    static void companies(@Autowired javax.sql.DataSource migratedByNow) throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("""
                    INSERT INTO electriplan.organisation (id, name, slug, status, seat_limit) VALUES
                      ('%1$s', 'Uploads A', 'uploads-a-p5', 'active', 10), ('%2$s', 'Uploads B', 'uploads-b-p5', 'active', 10);
                    INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES
                      ('%3$s', 'builder@p5.com', now()), ('%4$s', 'viewer@p5.com', now()), ('%5$s', 'owner@p5b.com', now());
                    INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES
                      ('%1$s', '%3$s', 'owner'), ('%1$s', '%4$s', 'viewer'), ('%2$s', '%5$s', 'owner');
                    """.formatted(A, B, BUILDER, VIEWER, B_OWNER));
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM electriplan.plan_stage_event WHERE organisation_id IN ('%s', '%s')".formatted(A, B));
            s.execute("DELETE FROM electriplan.organisation WHERE id IN ('%s', '%s')".formatted(A, B));
            s.execute("DELETE FROM electriplan.supabase_user WHERE id IN ('%s', '%s', '%s')".formatted(BUILDER, VIEWER, B_OWNER));
        }
    }

    @BeforeEach
    void analyserReadsAnyImage() throws IOException {
        given(analyser.analyse(any(), anyString(), anyString(), anyString(), any(), anyString())).willAnswer(call -> {
            ObjectNode plan = (ObjectNode) example();
            ((ObjectNode) plan.path("source")).put("imageUrl", (String) call.getArgument(3));
            return plan;
        });
    }

    // ---- helpers ----

    private ResultActions call(UUID person, MockHttpServletRequestBuilder request) throws Exception {
        UUID company = person.equals(B_OWNER) ? B : A;
        return mvc.perform(request.header("X-Organisation-Id", company.toString()).with(jwt().jwt(j -> j.subject(person.toString())
                .tokenValue("token-of-" + person))));
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    static JsonNode example() throws IOException {
        try (InputStream in = HouseUploadsPostgresTests.class.getClassLoader()
                .getResourceAsStream("contracts/examples/floor-plan/valid/analysed-two-bedroom-unit.json")) {
            return JSON.readTree(in);
        }
    }

    /** A real PNG, different for each seed. */
    static byte[] png(int seed) throws IOException {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        image.setRGB(seed % 40, 1, 0xFFFFFF);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String newHouse() throws Exception {
        ObjectNode project = JSON.createObjectNode().put("name", "Uploads " + UUID.randomUUID());
        project.putObject("site").put("state", "VIC");
        String projectId = body(call(BUILDER, post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(project.toString()))
                .andExpect(status().isCreated())).path("id").asText();
        return body(call(BUILDER, post("/api/projects/" + projectId + "/houses").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Type A\"}")).andExpect(status().isCreated())).path("id").asText();
    }

    private static MockHttpServletRequestBuilder upload(String houseId, byte[] bytes, String name) {
        return multipart("/api/houses/" + houseId + "/floor-plan/uploads").file(new MockMultipartFile("file", name, "image/png", bytes));
    }

    private static long count(String sql) throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    // ---- tests ----

    @Test
    void anUploadedImageIsKeptAnalysedAndBecomesTheDraft() throws Exception {
        String houseId = newHouse();
        byte[] image = png(1);

        JsonNode doc = body(call(BUILDER, upload(houseId, image, "C:\\plans\\unit.png")).andExpect(status().isOk()));

        assertThat(doc.path("state").asText()).isEqualTo("draft");
        assertThat(doc.path("document").path("rooms")).hasSize(4);
        String imageUrl = doc.path("document").path("source").path("imageUrl").asText();
        assertThat(imageUrl).matches("/api/files/[0-9a-f-]{36}");

        // The analyser was given the image, where the plan should point for it, and the caller's token.
        verify(analyser).analyse(eq(image), eq("C:\\plans\\unit.png"), eq("image/png"), eq(imageUrl), isNull(), eq("token-of-" + BUILDER));

        String fileId = imageUrl.substring("/api/files/".length());
        try (Connection c = owner(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("""
                SELECT f.storage_backend, f.storage_key, f.original_name, f.content_type, f.byte_size, f.image_width_px, f.image_height_px,
                       r.status, r.steps::text, r.started_at IS NOT NULL AND r.finished_at >= r.started_at, r.requested_by,
                       v.origin, v.analysis_run_id = r.id, v.source_file_id = f.id
                  FROM electriplan.stored_file f
                  JOIN electriplan.analysis_run r ON r.source_file_id = f.id
                  JOIN electriplan.floor_plan_version v ON v.analysis_run_id = r.id
                 WHERE f.id = '%s'""".formatted(fileId))) {
            assertThat(r.next()).isTrue();
            assertThat(r.getString(1)).isEqualTo("s3");
            assertThat(r.getString(2)).matches("organisations/" + A + "/files/[0-9a-f]{64}\\.png");
            assertThat(r.getString(3)).as("no folders from the browser").isEqualTo("unit.png");
            assertThat(r.getString(4)).isEqualTo("image/png");
            assertThat(r.getLong(5)).isEqualTo(image.length);
            assertThat(r.getInt(6)).isEqualTo(40);
            assertThat(r.getInt(7)).isEqualTo(30);
            assertThat(r.getString(8)).isEqualTo("succeeded");
            assertThat(r.getString(9)).contains("Image processed");
            assertThat(r.getBoolean(10)).isTrue();
            assertThat(r.getString(11)).isEqualTo(BUILDER.toString());
            assertThat(r.getString(12)).isEqualTo("analysis");
            assertThat(r.getBoolean(13)).isTrue();
            assertThat(r.getBoolean(14)).isTrue();
            assertThat(((InMemoryFileStore) store).objects.get(r.getString(2))).isEqualTo(image);
        }

        call(VIEWER, get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(image))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
    }

    @Test
    void theSameImageIsKeptOnce() throws Exception {
        String houseId = newHouse();
        byte[] image = png(2);
        String first = body(call(BUILDER, upload(houseId, image, "a.png")).andExpect(status().isOk()))
                .path("document").path("source").path("imageUrl").asText();
        int version = body(call(BUILDER, get("/api/houses/" + houseId + "/floor-plan"))).path("version").asInt();
        String second = body(call(BUILDER, upload(houseId, image, "b.png").param("version", String.valueOf(version)))
                .andExpect(status().isOk())).path("document").path("source").path("imageUrl").asText();

        assertThat(second).isEqualTo(first);
        assertThat(count("SELECT count(*) FROM electriplan.stored_file WHERE id = '%s'".formatted(first.substring(11)))).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM electriplan.analysis_run WHERE source_file_id = '%s'".formatted(first.substring(11))))
                .as("each upload is its own run").isEqualTo(2);
    }

    @Test
    void aFailedAnalysisIsRecordedWithItsReasonAndChangesNoPlan() throws Exception {
        String houseId = newHouse();
        given(analyser.analyse(any(), anyString(), anyString(), anyString(), any(), anyString()))
                .willThrow(new AnalysisFailedException("No walls were found in the image."));

        call(BUILDER, upload(houseId, png(3), "blank.png")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No walls were found in the image."));

        assertThat(count("""
                SELECT count(*) FROM electriplan.analysis_run r JOIN electriplan.plan_level l ON l.id = r.plan_level_id
                 WHERE l.plan_id = '%s' AND r.status = 'failed' AND r.error_message = 'No walls were found in the image.'
                   AND r.finished_at IS NOT NULL""".formatted(houseId))).isEqualTo(1);
        call(BUILDER, get("/api/houses/" + houseId + "/floor-plan")).andExpect(status().isNotFound());
    }

    @Test
    void anAnalyserThatIsDownIsA503() throws Exception {
        String houseId = newHouse();
        given(analyser.analyse(any(), anyString(), anyString(), anyString(), any(), anyString()))
                .willThrow(new AnalysisFailedException("The floor-plan analyser is not answering.", true, new RuntimeException()));
        call(BUILDER, upload(houseId, png(4), "p.png")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void onlyJpgAndPngImagesAreTaken() throws Exception {
        String houseId = newHouse();
        call(BUILDER, upload(houseId, "%PDF-1.7 not an image".getBytes(), "plan.pdf")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That is not a JPG or PNG image. Upload a JPG or PNG of the floor plan."));
        call(BUILDER, upload(houseId, new byte[0], "empty.png")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The uploaded file was empty."));
        call(BUILDER, multipart("/api/houses/" + houseId + "/floor-plan/uploads")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose an image of the floor plan to upload."));
        verify(analyser, never()).analyse(any(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void anUploadReplacesADraftOnlyWithItsVersion() throws Exception {
        String houseId = newHouse();
        call(BUILDER, upload(houseId, png(5), "a.png")).andExpect(status().isOk());
        call(BUILDER, upload(houseId, png(6), "b.png").param("version", "7")).andExpect(status().isConflict());
        call(BUILDER, upload(houseId, png(6), "b.png").param("version", "0")).andExpect(status().isOk());
    }

    @Test
    void imagesAreForTheirCompanyAndUploadsForFloorPlanEditors() throws Exception {
        String houseId = newHouse();
        String imageUrl = body(call(BUILDER, upload(houseId, png(7), "a.png")).andExpect(status().isOk()))
                .path("document").path("source").path("imageUrl").asText();

        call(B_OWNER, get(imageUrl)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No such file in this company."));
        call(B_OWNER, upload(houseId, png(8), "x.png")).andExpect(status().isNotFound());
        call(VIEWER, upload(houseId, png(9), "x.png")).andExpect(status().isForbidden());
        call(VIEWER, get("/api/files/not-a-uuid")).andExpect(status().isNotFound());
        mvc.perform(get(imageUrl)).andExpect(status().isUnauthorized());
    }

    @Test
    void anArchivedHouseTakesNoUploads() throws Exception {
        String houseId = newHouse();
        call(BUILDER, post("/api/houses/" + houseId + "/archive")).andExpect(status().isOk());
        call(BUILDER, upload(houseId, png(10), "a.png")).andExpect(status().isConflict());
        verify(analyser, never()).analyse(any(), anyString(), anyString(), anyString(), any(), anyString());
    }
}
