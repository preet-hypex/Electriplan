package com.hypex.electriplan.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypex.electriplan.PostgresApplicationTest;
import com.hypex.electriplan.TestDatabase;
import com.hypex.electriplan.users.SupabaseUsers;
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
 * Projects and houses end to end (P1), against Postgres as the API's own
 * login: row-level security, the database's references, stage history and
 * last-activity triggers all apply. Company A has an owner, a builder, an
 * electrician and a viewer; company B an owner.
 */
class ProjectsPostgresTests extends PostgresApplicationTest {

    static final UUID A = UUID.fromString("e1000000-0000-4000-8000-0000000000a1");
    static final UUID B = UUID.fromString("e1000000-0000-4000-8000-0000000000b1");
    static final UUID OWNER = UUID.fromString("e1000000-0000-4000-8000-000000000001");
    static final UUID BUILDER = UUID.fromString("e1000000-0000-4000-8000-000000000002");
    static final UUID ELECTRICIAN = UUID.fromString("e1000000-0000-4000-8000-000000000003");
    static final UUID VIEWER = UUID.fromString("e1000000-0000-4000-8000-000000000004");
    static final UUID B_OWNER = UUID.fromString("e1000000-0000-4000-8000-000000000005");

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
                      ('%1$s', 'Projects A', 'projects-a-p1', 'active', 10),
                      ('%2$s', 'Projects B', 'projects-b-p1', 'active', 10);
                    INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES
                      ('%3$s', 'owner@p1.com', now()), ('%4$s', 'builder@p1.com', now()), ('%5$s', 'sparky@p1.com', now()),
                      ('%6$s', 'viewer@p1.com', now()), ('%7$s', 'owner@p1b.com', now());
                    INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES
                      ('%1$s', '%3$s', 'owner'), ('%1$s', '%4$s', 'builder'), ('%1$s', '%5$s', 'electrician'),
                      ('%1$s', '%6$s', 'viewer'), ('%2$s', '%7$s', 'owner');
                    """.formatted(A, B, OWNER, BUILDER, ELECTRICIAN, VIEWER, B_OWNER));
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM electriplan.plan_stage_event WHERE organisation_id IN ('%s', '%s')".formatted(A, B));
            s.execute("DELETE FROM electriplan.organisation WHERE id IN ('%s', '%s')".formatted(A, B));
            s.execute("DELETE FROM electriplan.supabase_user WHERE id IN ('%s', '%s', '%s', '%s', '%s')"
                    .formatted(OWNER, BUILDER, ELECTRICIAN, VIEWER, B_OWNER));
        }
    }

    // ---- helpers ----

    private static MockHttpServletRequestBuilder in(UUID company, MockHttpServletRequestBuilder request, UUID person) {
        return request.header("X-Organisation-Id", company.toString())
                .with(jwt().jwt(j -> j.subject(person.toString())))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions call(UUID person, MockHttpServletRequestBuilder request) throws Exception {
        UUID company = person.equals(B_OWNER) ? B : A;
        return mvc.perform(in(company, request, person));
    }

    private static JsonNode body(ResultActions result) throws Exception {
        return JSON.readTree(result.andReturn().getResponse().getContentAsString());
    }

    static ObjectNode projectForm(String name) {
        ObjectNode form = JSON.createObjectNode().put("name", name);
        form.putObject("site").put("street", "12 Example St").put("suburb", "Brunswick").put("state", "VIC").put("postcode", "3056");
        return form;
    }

    private JsonNode createProject(UUID person, ObjectNode form) throws Exception {
        return body(call(person, post("/api/projects").content(form.toString())).andExpect(status().isCreated()));
    }

    private JsonNode addHouse(UUID person, String projectId, String name) throws Exception {
        return body(call(person, post("/api/projects/" + projectId + "/houses").content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()));
    }

    private static int number(String reference) {
        return Integer.parseInt(reference.substring("PRJ-".length()));
    }

    // ---- creating ----

    @Test
    void aNewProjectGetsTheNextReferenceAndSensibleDefaults() throws Exception {
        JsonNode first = createProject(BUILDER, projectForm("Lot 1 Smith St").put("distributor", "jemena").put("lotNumber", "1"));
        JsonNode second = createProject(OWNER, projectForm("Lot 2 Smith St"));

        assertThat(first.path("reference").asText()).matches("PRJ-\\d{6}");
        assertThat(number(second.path("reference").asText())).isEqualTo(number(first.path("reference").asText()) + 1);
        assertThat(first.path("name").asText()).isEqualTo("Lot 1 Smith St");
        assertThat(first.path("status").asText()).isEqualTo("active");
        assertThat(first.path("supplyPhases").asInt()).isEqualTo(1);
        assertThat(first.path("distributor").asText()).isEqualTo("jemena");
        assertThat(first.path("lotNumber").asText()).isEqualTo("1");
        assertThat(first.path("site").path("state").asText()).isEqualTo("VIC");
        assertThat(first.path("site").path("suburb").asText()).isEqualTo("Brunswick");
        assertThat(first.path("archived").asBoolean()).isFalse();
        assertThat(first.path("version").asInt()).isZero();
        assertThat(first.path("houses")).isEmpty();
        assertThat(first.path("createdAt").asText()).isNotBlank();
        assertThat(first.path("description").isNull()).isTrue();
    }

    @Test
    void referencesAreCountedPerCompany() throws Exception {
        JsonNode inB = createProject(B_OWNER, projectForm("B's first"));
        assertThat(inB.path("reference").asText()).isEqualTo("PRJ-000001");
        assertThat(createProject(B_OWNER, projectForm("B's second")).path("reference").asText()).isEqualTo("PRJ-000002");
    }

    @Test
    void fieldsAreCheckedAndEachProblemNamed() throws Exception {
        ObjectNode bad = JSON.createObjectNode().put("name", "  ");
        bad.putObject("site").put("postcode", "30560");
        JsonNode problem = body(call(BUILDER, post("/api/projects").content(bad.toString())).andExpect(status().isBadRequest()));

        assertThat(problem.path("message").asText()).isEqualTo("Check the highlighted fields.");
        assertThat(fields(problem)).containsExactly(
                Map.entry("name", "Give the project a name"),
                Map.entry("site.postcode", "A postcode is 4 digits"),
                Map.entry("site.state", "Choose the state the site is in"));

        JsonNode noSite = body(call(BUILDER, post("/api/projects").content("{\"name\":\"x\"}")).andExpect(status().isBadRequest()));
        assertThat(fields(noSite)).containsExactly(Map.entry("site", "Give the site's address, at least its state"));
    }

    @Test
    void theDistributorMustSupplyTheSitesState() throws Exception {
        JsonNode unknown = body(call(BUILDER, post("/api/projects").content(projectForm("x").put("distributor", "nope").toString()))
                .andExpect(status().isBadRequest()));
        assertThat(fields(unknown)).containsExactly(Map.entry("distributor",
                "No distributor has the code 'nope'. Choose one of ausnet_services, citipower, jemena, powercor, united_energy."));

        ObjectNode nsw = projectForm("Sydney job").put("distributor", "jemena");
        ((ObjectNode) nsw.path("site")).put("state", "NSW").remove("postcode");
        JsonNode wrongState = body(call(BUILDER, post("/api/projects").content(nsw.toString())).andExpect(status().isBadRequest()));
        assertThat(fields(wrongState)).containsExactly(Map.entry("distributor",
                "Jemena supplies VIC, not NSW. No distributor in NSW is set up yet: leave it empty."));

        nsw.remove("distributor");
        assertThat(createProject(BUILDER, nsw).path("site").path("state").asText()).isEqualTo("NSW");
    }

    @Test
    void supplyIsSingleOrThreePhase() throws Exception {
        JsonNode two = body(call(BUILDER, post("/api/projects").content(projectForm("x").put("supplyPhases", 2).toString()))
                .andExpect(status().isBadRequest()));
        assertThat(fields(two)).containsExactly(Map.entry("supplyPhases", "Supply is single-phase (1) or three-phase (3)"));
        assertThat(createProject(BUILDER, projectForm("Big house").put("supplyPhases", 3)).path("supplyPhases").asInt()).isEqualTo(3);
    }

    @Test
    void unknownValuesAreRefusedPlainly() throws Exception {
        ObjectNode form = projectForm("x");
        ((ObjectNode) form.path("site")).put("state", "XX");
        call(BUILDER, post("/api/projects").content(form.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.startsWith("The request could not be read")));
    }

    private static List<Map.Entry<String, String>> fields(JsonNode problem) {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        problem.path("errors").forEach(e -> out.add(Map.entry(e.path("field").asText(), e.path("message").asText())));
        return out;
    }

    // ---- listing ----

    @Test
    void theListShowsNewestActivityFirstWithHousesByStage() throws Exception {
        String tag = "List-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode older = createProject(BUILDER, projectForm(tag + " older"));
        JsonNode newer = createProject(BUILDER, projectForm(tag + " newer"));

        JsonNode page = body(call(VIEWER, get("/api/projects").param("q", tag)).andExpect(status().isOk()));
        assertThat(names(page)).containsExactly(tag + " newer", tag + " older");

        addHouse(BUILDER, older.path("id").asText(), "Type A");
        addHouse(BUILDER, older.path("id").asText(), "Type B");
        page = body(call(VIEWER, get("/api/projects").param("q", tag)).andExpect(status().isOk()));
        assertThat(names(page)).as("adding a house is activity in its project").containsExactly(tag + " older", tag + " newer");

        JsonNode row = page.path("items").get(0);
        assertThat(row.path("reference").asText()).isEqualTo(older.path("reference").asText());
        assertThat(row.path("suburb").asText()).isEqualTo("Brunswick");
        assertThat(row.path("state").asText()).isEqualTo("VIC");
        assertThat(row.path("houseCount").asLong()).isEqualTo(2);
        assertThat(row.path("stages").get(0).path("stage").asText()).isEqualTo("awaiting_upload");
        assertThat(row.path("stages").get(0).path("count").asLong()).isEqualTo(2);
        assertThat(page.path("items").get(1).path("houseCount").asLong()).isZero();
        assertThat(page.path("total").asLong()).isEqualTo(2);
        assertThat(newer.path("id").asText()).isEqualTo(page.path("items").get(1).path("id").asText());
    }

    @Test
    void theListSearchesFiltersAndPages() throws Exception {
        String tag = "Search-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode one = createProject(BUILDER, projectForm(tag + " one"));
        createProject(BUILDER, projectForm(tag + " two"));
        createProject(BUILDER, projectForm(tag + " three").put("status", "on_hold"));

        assertThat(names(body(call(VIEWER, get("/api/projects").param("q", one.path("reference").asText())))))
                .containsExactly(tag + " one");
        assertThat(names(body(call(VIEWER, get("/api/projects").param("q", tag).param("status", "on_hold")))))
                .containsExactly(tag + " three");

        JsonNode firstPage = body(call(VIEWER, get("/api/projects").param("q", tag).param("size", "2")));
        assertThat(firstPage.path("items")).hasSize(2);
        assertThat(firstPage.path("total").asLong()).isEqualTo(3);
        JsonNode secondPage = body(call(VIEWER, get("/api/projects").param("q", tag).param("size", "2").param("page", "1")));
        assertThat(secondPage.path("items")).hasSize(1);

        assertThat(names(body(call(VIEWER, get("/api/projects").param("q", "100%_" + tag))))).as("wildcards are literal").isEmpty();
        call(VIEWER, get("/api/projects").param("size", "0")).andExpect(status().isBadRequest());
        call(VIEWER, get("/api/projects").param("size", "101")).andExpect(status().isBadRequest());
        call(VIEWER, get("/api/projects").param("status", "sideways")).andExpect(status().isBadRequest());
    }

    private static List<String> names(JsonNode page) {
        List<String> out = new ArrayList<>();
        page.path("items").forEach(p -> out.add(p.path("name").asText()));
        return out;
    }

    // ---- changing ----

    @Test
    void aChangeNamesTheVersionItWasMadeFrom() throws Exception {
        JsonNode project = createProject(BUILDER, projectForm("Before"));
        String url = "/api/projects/" + project.path("id").asText();

        JsonNode changed = body(call(OWNER, put(url).content(projectForm("After").put("description", "Two storeys")
                .put("version", 0).put("status", "on_hold").toString())).andExpect(status().isOk()));
        assertThat(changed.path("name").asText()).isEqualTo("After");
        assertThat(changed.path("description").asText()).isEqualTo("Two storeys");
        assertThat(changed.path("status").asText()).isEqualTo("on_hold");
        assertThat(changed.path("version").asInt()).isEqualTo(1);
        assertThat(changed.path("reference").asText()).isEqualTo(project.path("reference").asText());

        call(OWNER, put(url).content(projectForm("Stale").put("version", 0).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ProjectsErrors.CHANGED_SINCE));
        JsonNode noVersion = body(call(OWNER, put(url).content(projectForm("No version").toString())).andExpect(status().isBadRequest()));
        assertThat(fields(noVersion)).extracting(Map.Entry::getKey).containsExactly("version");

        assertThat(body(call(VIEWER, get(url))).path("name").asText()).isEqualTo("After");
    }

    @Test
    void anArchivedProjectLeavesTheListAndIsReadOnlyUntilRestored() throws Exception {
        String tag = "Archive-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode project = createProject(BUILDER, projectForm(tag));
        String url = "/api/projects/" + project.path("id").asText();

        JsonNode archived = body(call(BUILDER, post(url + "/archive")).andExpect(status().isOk()));
        assertThat(archived.path("archived").asBoolean()).isTrue();
        assertThat(archived.path("archivedAt").asText()).isNotBlank();
        assertThat(names(body(call(VIEWER, get("/api/projects").param("q", tag))))).isEmpty();
        assertThat(names(body(call(VIEWER, get("/api/projects").param("q", tag).param("archived", "true"))))).containsExactly(tag);

        call(BUILDER, put(url).content(projectForm("x").put("version", archived.path("version").asInt()).toString()))
                .andExpect(status().isConflict());
        call(BUILDER, post(url + "/houses").content("{\"name\":\"x\"}")).andExpect(status().isConflict());

        JsonNode restored = body(call(BUILDER, post(url + "/restore")).andExpect(status().isOk()));
        assertThat(restored.path("archived").asBoolean()).isFalse();
        assertThat(names(body(call(VIEWER, get("/api/projects").param("q", tag))))).containsExactly(tag);
    }

    // ---- houses ----

    @Test
    void aHouseStartsAwaitingItsFloorPlanWithItsGroundFloor() throws Exception {
        JsonNode project = createProject(BUILDER, projectForm("Houses"));
        JsonNode house = addHouse(BUILDER, project.path("id").asText(), "Type A");

        assertThat(house.path("name").asText()).isEqualTo("Type A");
        assertThat(house.path("dwellingType").asText()).isEqualTo("house");
        assertThat(house.path("stage").asText()).isEqualTo("awaiting_upload");
        assertThat(house.path("storeys").asInt()).isEqualTo(1);
        assertThat(house.path("version").asInt()).isZero();
        assertThat(house.path("project").path("reference").asText()).isEqualTo(project.path("reference").asText());
        assertThat(house.path("levels")).hasSize(1);
        assertThat(house.path("levels").get(0).path("name").asText()).isEqualTo("Ground floor");
        assertThat(house.path("levels").get(0).path("ordinal").asInt()).isZero();
        assertThat(house.path("levels").get(0).path("ceilingHeightMm").asInt()).isEqualTo(2550);
        assertThat(house.path("levels").get(0).path("currentFloorPlanVersionId").isNull()).isTrue();

        try (Connection c = owner(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT to_stage, actor_id FROM electriplan.plan_stage_event WHERE plan_id = '"
                     + house.path("id").asText() + "'")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getString(1)).isEqualTo("awaiting_upload");
            assertThat(r.getString(2)).as("the stage history records who").isEqualTo(BUILDER.toString());
        }

        JsonNode withHouse = body(call(VIEWER, get("/api/projects/" + project.path("id").asText())));
        assertThat(withHouse.path("houses")).hasSize(1);
        assertThat(withHouse.path("houses").get(0).path("stage").asText()).isEqualTo("awaiting_upload");
        assertThat(body(call(VIEWER, get("/api/houses/" + house.path("id").asText()))).path("name").asText()).isEqualTo("Type A");
    }

    @Test
    void aHouseIsChangedArchivedAndRestored() throws Exception {
        JsonNode project = createProject(BUILDER, projectForm("House changes"));
        JsonNode house = addHouse(BUILDER, project.path("id").asText(), "Type A");
        String url = "/api/houses/" + house.path("id").asText();

        JsonNode renamed = body(call(BUILDER, put(url).content("{\"name\":\"Type A2\",\"dwellingType\":\"townhouse\",\"version\":0}"))
                .andExpect(status().isOk()));
        assertThat(renamed.path("name").asText()).isEqualTo("Type A2");
        assertThat(renamed.path("dwellingType").asText()).isEqualTo("townhouse");
        assertThat(renamed.path("version").asInt()).isEqualTo(1);
        call(BUILDER, put(url).content("{\"name\":\"Stale\",\"version\":0}")).andExpect(status().isConflict());

        assertThat(body(call(BUILDER, post(url + "/archive")).andExpect(status().isOk())).path("archived").asBoolean()).isTrue();
        call(BUILDER, put(url).content("{\"name\":\"x\",\"version\":2}")).andExpect(status().isConflict());

        call(BUILDER, post("/api/projects/" + project.path("id").asText() + "/archive")).andExpect(status().isOk());
        call(BUILDER, post(url + "/restore")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Its project is archived. Restore the project first."));
        call(BUILDER, post("/api/projects/" + project.path("id").asText() + "/restore")).andExpect(status().isOk());
        assertThat(body(call(BUILDER, post(url + "/restore")).andExpect(status().isOk())).path("archived").asBoolean()).isFalse();
    }

    @Test
    void aHouseNeedsAName() throws Exception {
        JsonNode project = createProject(BUILDER, projectForm("Nameless"));
        JsonNode problem = body(call(BUILDER, post("/api/projects/" + project.path("id").asText() + "/houses").content("{}"))
                .andExpect(status().isBadRequest()));
        assertThat(fields(problem)).containsExactly(Map.entry("name", "Give the house a name"));
    }

    // ---- who may ----

    @Test
    void everyoneMaySeeOnlyOwnersAdminsAndBuildersMayChange() throws Exception {
        JsonNode project = createProject(OWNER, projectForm("Permissions"));
        String id = project.path("id").asText();

        call(VIEWER, get("/api/projects/" + id)).andExpect(status().isOk());
        call(ELECTRICIAN, get("/api/projects")).andExpect(status().isOk());

        call(VIEWER, post("/api/projects").content(projectForm("x").toString())).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("project.edit")));
        call(ELECTRICIAN, post("/api/projects").content(projectForm("x").toString())).andExpect(status().isForbidden());
        call(ELECTRICIAN, post("/api/projects/" + id + "/houses").content("{\"name\":\"x\"}")).andExpect(status().isForbidden());
        call(VIEWER, post("/api/projects/" + id + "/archive")).andExpect(status().isForbidden());
    }

    @Test
    void anotherCompanysProjectsAndHousesDoNotExist() throws Exception {
        JsonNode project = createProject(OWNER, projectForm("A's secret"));
        JsonNode house = addHouse(OWNER, project.path("id").asText(), "A's house");
        String id = project.path("id").asText();

        call(B_OWNER, get("/api/projects/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No project with that id in this company."));
        call(B_OWNER, put("/api/projects/" + id).content(projectForm("Hijack").put("version", 0).toString())).andExpect(status().isNotFound());
        call(B_OWNER, post("/api/projects/" + id + "/archive")).andExpect(status().isNotFound());
        call(B_OWNER, post("/api/projects/" + id + "/houses").content("{\"name\":\"x\"}")).andExpect(status().isNotFound());
        call(B_OWNER, get("/api/houses/" + house.path("id").asText())).andExpect(status().isNotFound());
        call(B_OWNER, put("/api/houses/" + house.path("id").asText()).content("{\"name\":\"x\",\"version\":0}")).andExpect(status().isNotFound());
        assertThat(names(body(call(B_OWNER, get("/api/projects").param("q", "A's secret"))))).isEmpty();

        assertThat(body(call(OWNER, get("/api/projects/" + id))).path("name").asText()).isEqualTo("A's secret");
    }

    @Test
    void aMalformedIdIsABadRequest() throws Exception {
        call(VIEWER, get("/api/projects/not-a-uuid")).andExpect(status().isBadRequest());
        call(VIEWER, get("/api/projects/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
