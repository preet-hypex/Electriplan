package com.hypex.electriplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What the API's own database login (electriplan_api, a member of
 * electriplan_app from migration V4) may and may not do. Plain SQL on purpose:
 * this tests database privileges, below anything Hibernate would show.
 *
 * <p>Every statement runs in a transaction that is rolled back.
 */
@EnabledIfEnvironmentVariable(named = "APP_TEST_DB_URL", matches = "jdbc:postgresql:.+")
class ApplicationRolePostgresTests {

    private static final String ORG = "dddddddd-0000-4000-8000-000000000004";
    private static Connection api;

    @BeforeAll
    static void migrateAndConnectAsTheApi() throws SQLException {
        Flyway.configure().dataSource(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password())
                .schemas("electriplan").defaultSchema("electriplan").locations("classpath:db/migration").load().migrate();
        api = DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.apiUser(), TestDatabase.apiPassword());
        api.setAutoCommit(false);
    }

    @AfterAll
    static void disconnect() throws SQLException {
        api.rollback();
        api.close();
    }

    private static void run(String sql) throws SQLException {
        try (Statement s = api.createStatement()) {
            s.execute(sql);
        }
    }

    /** Runs a statement that must be refused, inside a savepoint so the transaction carries on. */
    private static void refused(String sql, String reason) throws SQLException {
        var savepoint = api.setSavepoint();
        try {
            assertThatThrownBy(() -> run(sql)).as(sql).isInstanceOf(SQLException.class).hasMessageContaining(reason);
        } finally {
            api.rollback(savepoint);
        }
    }

    private static long count(String sql) throws SQLException {
        try (Statement s = api.createStatement(); ResultSet r = s.executeQuery(sql)) {
            r.next();
            return r.getLong(1);
        }
    }

    @Test
    void isNeitherASuperuserNorAbleToBypassRowLevelSecurity() throws SQLException {
        try (Statement s = api.createStatement();
             ResultSet r = s.executeQuery("""
                     SELECT rolsuper, rolbypassrls, rolcreatedb, rolcreaterole,
                            pg_has_role(current_user, 'electriplan_app', 'MEMBER')
                       FROM pg_roles WHERE rolname = current_user""")) {
            r.next();
            assertThat(r.getBoolean(1)).as("superuser").isFalse();
            assertThat(r.getBoolean(2)).as("bypasses row-level security").isFalse();
            assertThat(r.getBoolean(3)).as("may create databases").isFalse();
            assertThat(r.getBoolean(4)).as("may create roles").isFalse();
            assertThat(r.getBoolean(5)).as("member of electriplan_app").isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE TABLE electriplan.sneaky (id int)",
            "ALTER TABLE electriplan.project ADD COLUMN sneaky int",
            "DROP TABLE electriplan.quote_line",
            "TRUNCATE electriplan.organisation",
            "ALTER TABLE electriplan.project DISABLE ROW LEVEL SECURITY",
            "DROP POLICY tenant_isolation ON electriplan.project",
            "CREATE FUNCTION electriplan.sneaky() RETURNS int LANGUAGE sql AS 'SELECT 1'"})
    void cannotChangeTheSchema(String ddl) throws SQLException {
        refused(ddl, "");
    }

    @Test
    void cannotTouchFlywaysHistory() throws SQLException {
        refused("SELECT * FROM electriplan.flyway_schema_history", "permission denied");
        refused("DELETE FROM electriplan.flyway_schema_history", "permission denied");
    }

    @Test
    void readsButNeverWritesReferenceData() throws SQLException {
        assertThat(count("SELECT count(*) FROM electriplan.electricity_distributor")).isGreaterThanOrEqualTo(5);
        assertThat(count("SELECT count(*) FROM electriplan.plan_stage")).isGreaterThan(0);
        refused("INSERT INTO electriplan.electricity_distributor VALUES ('sneaky', 'Sneaky', 'VIC')", "permission denied");
        refused("UPDATE electriplan.plan_stage SET label = 'x'", "permission denied");
        refused("DELETE FROM electriplan.plan_stage_transition", "permission denied");
    }

    @Test
    void isLimitedToTheCurrentCompanyByRowLevelSecurity() throws SQLException {
        run("SELECT set_config('electriplan.organisation_id', '" + ORG + "', true)");
        run("INSERT INTO electriplan.organisation (id, name, slug) VALUES ('" + ORG + "', 'Role Test', 'role-test') ON CONFLICT DO NOTHING");
        run("INSERT INTO electriplan.project (organisation_id, reference, name) VALUES ('" + ORG + "', 'PRJ-1', 'Site')");
        assertThat(count("SELECT count(*) FROM electriplan.project")).isEqualTo(1);

        run("SELECT set_config('electriplan.organisation_id', '', true)");
        assertThat(count("SELECT count(*) FROM electriplan.project")).as("no company set").isZero();
        assertThat(count("SELECT count(*) FROM electriplan.organisation")).as("no company set").isZero();
        refused("INSERT INTO electriplan.project (organisation_id, reference, name) VALUES ('" + ORG + "', 'PRJ-2', 'x')",
                "row-level security");
    }

    @Test
    void writesHistoryButNeverRewritesIt() throws SQLException {
        run("SELECT set_config('electriplan.organisation_id', '" + ORG + "', true)");
        run("INSERT INTO electriplan.organisation (id, name, slug) VALUES ('" + ORG + "', 'Audit Test', 'audit-test') ON CONFLICT DO NOTHING");
        run("INSERT INTO electriplan.audit_event (organisation_id, entity_type, action) VALUES ('" + ORG + "', 'project', 'created')");
        refused("UPDATE electriplan.audit_event SET action = 'edited'", "permission denied");
        refused("DELETE FROM electriplan.audit_event", "permission denied");
        refused("DELETE FROM electriplan.plan_stage_event", "permission denied");
    }

    @Test
    void doesItsOrdinaryWork() throws SQLException {
        // The scaffold's user copy and the reference endpoint work as before.
        run("INSERT INTO electriplan.supabase_user (id, email, created_at) VALUES ('eeeeeeee-0000-4000-8000-000000000005', 'r@t.com', now())");
        run("UPDATE electriplan.supabase_user SET email = 's@t.com' WHERE id = 'eeeeeeee-0000-4000-8000-000000000005'");
        run("DELETE FROM electriplan.supabase_user WHERE id = 'eeeeeeee-0000-4000-8000-000000000005'");
        assertThat(count("SELECT electriplan.seats_in_use('" + ORG + "')")).isZero();
    }
}
