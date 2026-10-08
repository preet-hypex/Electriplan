package com.hypex.electriplan.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypex.electriplan.PostgresApplicationTest;
import com.hypex.electriplan.TestDatabase;
import com.hypex.electriplan.users.SupabaseUsers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Permissions end to end, against Postgres as the API's own login: one company
 * with a person in every role, and the outcome for each derived from
 * PermissionMatrix itself, so the HTTP behaviour cannot drift from the matrix.
 */
class PermissionsPostgresTests extends PostgresApplicationTest {

    static final UUID COMPANY = UUID.fromString("d4000000-0000-4000-8000-0000000000d4");
    static final Map<MemberRole, UUID> PEOPLE = Map.of(
            MemberRole.OWNER, UUID.fromString("d4000000-0000-4000-8000-000000000001"),
            MemberRole.ADMIN, UUID.fromString("d4000000-0000-4000-8000-000000000002"),
            MemberRole.BUILDER, UUID.fromString("d4000000-0000-4000-8000-000000000003"),
            MemberRole.ELECTRICIAN, UUID.fromString("d4000000-0000-4000-8000-000000000004"),
            MemberRole.VIEWER, UUID.fromString("d4000000-0000-4000-8000-000000000005"));

    @Autowired MockMvc mvc;
    @MockitoBean SupabaseUsers users;

    private static Connection owner() throws SQLException {
        return DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password());
    }

    @BeforeAll
    static void company(@Autowired javax.sql.DataSource migratedByNow) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                INSERT INTO electriplan.organisation (id, name, slug, status, seat_limit) VALUES ('%s', 'Roles Co', 'roles-co-t4', 'active', 8);
                """.formatted(COMPANY));
        for (MemberRole role : MemberRole.values()) {
            UUID person = PEOPLE.get(role);
            sql.append("INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES ('%s', '%s@t4.com', now()) ON CONFLICT DO NOTHING;\n"
                    .formatted(person, role.code()));
            sql.append("INSERT INTO electriplan.organisation_member (organisation_id, user_id, role) VALUES ('%s', '%s', '%s');\n"
                    .formatted(COMPANY, person, role.code()));
        }
        // Seats: 4 seated members (not the viewer) + 1 pending builder invitation = 5.
        // A pending viewer invitation and an expired one hold no seat.
        String owner = PEOPLE.get(MemberRole.OWNER).toString();
        sql.append("""
                INSERT INTO electriplan.organisation_invitation (organisation_id, email, role, token_sha256, invited_by, expires_at, created_at) VALUES
                  ('%1$s', 'next@t4.com', 'builder', sha256('t4-1'), '%2$s', now() + interval '7 days', now()),
                  ('%1$s', 'home@t4.com', 'viewer', sha256('t4-2'), '%2$s', now() + interval '7 days', now()),
                  ('%1$s', 'late@t4.com', 'builder', sha256('t4-3'), '%2$s', now() - interval '1 day', now() - interval '8 days');
                """.formatted(COMPANY, owner));
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute(sql.toString());
        }
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        try (Connection c = owner(); Statement s = c.createStatement()) {
            s.execute("DELETE FROM electriplan.organisation WHERE id = '%s';".formatted(COMPANY));
            for (UUID person : PEOPLE.values()) {
                s.execute("DELETE FROM electriplan.supabase_user WHERE id = '%s';".formatted(person));
            }
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder in(String path) {
        return get(path).header("X-Organisation-Id", COMPANY.toString());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(MemberRole role) {
        return jwt().jwt(j -> j.subject(PEOPLE.get(role).toString()));
    }

    @ParameterizedTest(name = "{0} gets exactly the matrix's permissions")
    @EnumSource(MemberRole.class)
    void theCurrentCompanyListsWhatTheRoleMayDo(MemberRole role) throws Exception {
        String body = mvc.perform(in("/api/organisations/current").with(as(role)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role.code()))
                .andReturn().getResponse().getContentAsString();
        List<String> granted = toList(new ObjectMapper().readTree(body).path("permissions"));
        assertThat(granted).containsExactlyElementsOf(
                PermissionMatrix.permissions(role).stream().map(Permission::code).toList());
    }

    private static List<String> toList(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    @ParameterizedTest(name = "licence for {0}")
    @EnumSource(MemberRole.class)
    void theLicenceIsForRolesWithLicenceView(MemberRole role) throws Exception {
        var result = mvc.perform(in("/api/organisations/current/licence").with(as(role)));
        if (PermissionMatrix.allows(role, Permission.LICENCE_VIEW)) {
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("active"))
                    .andExpect(jsonPath("$.seatLimit").value(8))
                    .andExpect(jsonPath("$.seatsInUse").value(5));
        } else {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("As a")))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("licence.view")));
        }
    }

    @Test
    void hibernatesSeatCountAgreesWithTheDatabaseRule() throws Exception {
        long database;
        try (Connection c = owner(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT electriplan.seats_in_use('%s')".formatted(COMPANY))) {
            r.next();
            database = r.getLong(1);
        }
        mvc.perform(in("/api/organisations/current/licence").with(as(MemberRole.OWNER)))
                .andExpect(jsonPath("$.seatsInUse").value(database));
        assertThat(database).isEqualTo(5);
    }

    @Test
    void anOutsiderGetsNoFurtherThanTheCompanyCheck() throws Exception {
        UUID stranger = UUID.fromString("d4000000-0000-4000-8000-0000000000ff");
        mvc.perform(in("/api/organisations/current/licence").with(jwt().jwt(j -> j.subject(stranger.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of that company, or it is closed."));
    }
}
