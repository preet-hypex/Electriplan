package com.hypex.electriplan.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import com.hypex.electriplan.PostgresApplicationTest;
import com.hypex.electriplan.TestDatabase;
import com.hypex.electriplan.users.SupabaseUsers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Company context end to end, against Postgres with row-level security
 * applying (the API works as electriplan_api):
 *
 * <pre>
 *   alice  owner of Acme, viewer of Bolt
 *   bob    builder of Bolt
 *   carol  suspended member of Acme
 *   dave   member of Gone Co, which is closed
 *   erin   a member of nothing
 * </pre>
 *
 * The fixture is written as the owner with plain SQL: seeding companies is the
 * operator's job (story T6), and the API cannot write other companies' rows.
 */
class CompanyContextPostgresTests extends PostgresApplicationTest {

    static final UUID ACME = UUID.fromString("a1000000-0000-4000-8000-0000000000a1");
    static final UUID BOLT = UUID.fromString("b1000000-0000-4000-8000-0000000000b1");
    static final UUID GONE = UUID.fromString("c1000000-0000-4000-8000-0000000000c1");
    static final UUID ALICE = UUID.fromString("a1a1a1a1-0000-4000-8000-000000000001");
    static final UUID BOB = UUID.fromString("b0b0b0b0-0000-4000-8000-000000000002");
    static final UUID CAROL = UUID.fromString("ca401000-0000-4000-8000-000000000003");
    static final UUID DAVE = UUID.fromString("da7e0000-0000-4000-8000-000000000004");
    static final UUID ERIN = UUID.fromString("e4140000-0000-4000-8000-000000000005");

    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired OrganisationRepository organisations;
    @Autowired MembershipRepository memberships;
    @MockitoBean SupabaseUsers users;

    private static void asOwner(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password());
             Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    @BeforeAll
    static void companies(@Autowired javax.sql.DataSource migratedByNow) throws SQLException {
        asOwner("""
                INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES
                  ('%s', 'alice@t.com', now()), ('%s', 'bob@t.com', now()), ('%s', 'carol@t.com', now()),
                  ('%s', 'dave@t.com', now()), ('%s', 'erin@t.com', now()) ON CONFLICT DO NOTHING;
                INSERT INTO electriplan.organisation (id, name, slug, status, seat_limit) VALUES
                  ('%s', 'Acme Homes', 'acme-homes-t3', 'active', 5),
                  ('%s', 'Bolt Electrical', 'bolt-electrical-t3', 'suspended', 5);
                INSERT INTO electriplan.organisation (id, name, slug, status, closed_at) VALUES
                  ('%s', 'Gone Co', 'gone-co-t3', 'closed', now());
                INSERT INTO electriplan.organisation_member (organisation_id, user_id, role, status) VALUES
                  ('%s', '%s', 'owner', 'active'),
                  ('%s', '%s', 'viewer', 'active'),
                  ('%s', '%s', 'builder', 'active'),
                  ('%s', '%s', 'builder', 'suspended'),
                  ('%s', '%s', 'builder', 'active');
                """.formatted(ALICE, BOB, CAROL, DAVE, ERIN, ACME, BOLT, GONE,
                ACME, ALICE, BOLT, ALICE, BOLT, BOB, ACME, CAROL, GONE, DAVE));
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        asOwner("""
                DELETE FROM electriplan.organisation WHERE id IN ('%s', '%s', '%s');
                DELETE FROM electriplan.supabase_user WHERE id IN ('%s', '%s', '%s', '%s', '%s');
                """.formatted(ACME, BOLT, GONE, ALICE, BOB, CAROL, DAVE, ERIN));
    }

    @AfterEach
    void noSessionLeftBehind() {
        assertThat(TenantSession.company()).isNull();
        assertThat(TenantSession.actorId()).isNull();
    }

    private static RequestPostProcessor as(UUID user) {
        return jwt().jwt(j -> j.subject(user.toString()));
    }

    private static MockHttpServletRequestBuilder current(UUID company) {
        return get("/api/organisations/current").header("X-Organisation-Id", company.toString());
    }

    // -- the company switcher --------------------------------------------------------------------

    @Test
    void listsOnlyTheCallersOwnUsableCompanies() throws Exception {
        mvc.perform(get("/api/organisations").with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Acme Homes"))
                .andExpect(jsonPath("$[0].role").value("owner"))
                .andExpect(jsonPath("$[0].licence").value("active"))
                .andExpect(jsonPath("$[1].name").value("Bolt Electrical"))
                .andExpect(jsonPath("$[1].role").value("viewer"));
        mvc.perform(get("/api/organisations").with(as(BOB)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bolt Electrical"));
    }

    @Test
    void suspendedMembershipsAndClosedCompaniesAreNotListed() throws Exception {
        mvc.perform(get("/api/organisations").with(as(CAROL))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/organisations").with(as(DAVE))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/organisations").with(as(ERIN))).andExpect(jsonPath("$.length()").value(0));
    }

    // -- choosing the company --------------------------------------------------------------------

    @Test
    void theHeaderChoosesTheCompanyAndTheRoleComesWithIt() throws Exception {
        mvc.perform(current(ACME).with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACME.toString()))
                .andExpect(jsonPath("$.name").value("Acme Homes"))
                .andExpect(jsonPath("$.role").value("owner"))
                .andExpect(jsonPath("$.seatLimit").value(5));
        mvc.perform(current(BOLT).with(as(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("viewer"))
                .andExpect(jsonPath("$.licence").value("suspended"));
    }

    @Test
    void oneCompanyNeedsNoHeaderSeveralDo() throws Exception {
        mvc.perform(get("/api/organisations/current").with(as(BOB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bolt Electrical"));
        mvc.perform(get("/api/organisations/current").with(as(ALICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You belong to 2 companies: choose one with the X-Organisation-Id header."));
    }

    // -- refusals ----------------------------------------------------------------------------------

    @Test
    void anotherCompanysIdIsForbiddenAndLooksLikeAnUnknownOne() throws Exception {
        String same = "You are not a member of that company, or it is closed.";
        mvc.perform(current(ACME).with(as(BOB))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(same));
        mvc.perform(current(UUID.randomUUID()).with(as(BOB))).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value(same));
    }

    @Test
    void suspendedMembersClosedCompaniesAndOutsidersAreForbidden() throws Exception {
        mvc.perform(current(ACME).with(as(CAROL))).andExpect(status().isForbidden());
        mvc.perform(current(GONE).with(as(DAVE))).andExpect(status().isForbidden());
        mvc.perform(get("/api/organisations/current").with(as(ERIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You are not a member of any company yet. Ask a company owner or admin to invite you."));
    }

    @Test
    void aHeaderThatIsNotAnIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/organisations/current").header("X-Organisation-Id", "acme").with(as(ALICE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signingInIsStillRequired() throws Exception {
        mvc.perform(current(ACME)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/organisations")).andExpect(status().isUnauthorized());
    }

    @Test
    void endpointsThatAreNotCompanyScopedNeedNoCompany() throws Exception {
        mvc.perform(get("/api/reference/distributors").with(as(ERIN))).andExpect(status().isOk());
    }

    @Test
    void nothingCarriesOverToTheNextRequest() throws Exception {
        mvc.perform(current(ACME).with(as(ALICE))).andExpect(status().isOk());
        mvc.perform(get("/api/organisations/current").with(as(ERIN))).andExpect(status().isForbidden());
    }

    // -- isolation in the database -------------------------------------------------------------

    private <T> T inTransaction(CompanyContext company, java.util.function.Supplier<T> work) {
        try {
            TenantSession.company(company);
            return new TransactionTemplate(transactions).execute(status -> work.get());
        } finally {
            TenantSession.clear();
        }
    }

    @Test
    void aTransactionSeesOnlyItsCompanysRows() {
        List<UUID> acme = inTransaction(new CompanyContext(ACME, ALICE, MemberRole.OWNER, LicenceStatus.ACTIVE),
                () -> organisations.findAll().stream().map(OrganisationEntity::getId).filter(this::ours).toList());
        assertThat(acme).containsExactly(ACME);

        List<UUID> boltMembers = inTransaction(new CompanyContext(BOLT, BOB, MemberRole.BUILDER, LicenceStatus.SUSPENDED),
                () -> memberships.findAll().stream().map(m -> m.getId().getUserId()).toList());
        assertThat(boltMembers).containsExactlyInAnyOrder(ALICE, BOB);
    }

    @Test
    void insideACompanyTheCallersOtherCompaniesAreInvisibleToo() {
        // Alice belongs to Acme and Bolt; working in Acme she sees Acme only.
        CompanyContext acme = new CompanyContext(ACME, ALICE, MemberRole.OWNER, LicenceStatus.ACTIVE);
        assertThat(inTransaction(acme, () -> organisations.findAll().stream().map(OrganisationEntity::getId).filter(this::ours).toList()))
                .containsExactly(ACME);
        assertThat(inTransaction(acme, () -> memberships.findAll().stream().map(m -> m.getId().getOrganisationId()).distinct().toList()))
                .containsExactly(ACME);
    }

    @Test
    void withOnlyTheActorSetTheSwitcherSeesTheirOwnCompanies() {
        try {
            TenantSession.actor(ALICE);
            List<UUID> seen = new TransactionTemplate(transactions).execute(status ->
                    organisations.findAll().stream().map(OrganisationEntity::getId).filter(this::ours).toList());
            assertThat(seen).containsExactlyInAnyOrder(ACME, BOLT);
        } finally {
            TenantSession.clear();
        }
    }

    @Test
    void withNoCompanyTheCompanyTablesAreEmpty() {
        long seen = new TransactionTemplate(transactions).execute(status -> organisations.count() + memberships.count());
        assertThat(seen).isZero();
    }

    private boolean ours(UUID id) {
        return id.equals(ACME) || id.equals(BOLT) || id.equals(GONE);
    }
}
