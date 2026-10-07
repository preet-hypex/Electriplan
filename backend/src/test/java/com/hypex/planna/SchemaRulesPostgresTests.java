package com.hypex.planna;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;

/**
 * The integrity rules, lifecycle and tenant isolation the core-domain schema
 * promises, checked against a real Postgres: plan stages, frozen versions,
 * review sign-off, quote totals, and one organisation never seeing another's
 * rows. The checks are in db/schema-rules.sql.
 *
 * <p>They run as {@value #RUNTIME_ROLE}, an ordinary role, because a superuser
 * (which the local and CI databases connect as) bypasses row-level security.
 * Everything happens in one transaction that is rolled back.
 */
@EnabledIfEnvironmentVariable(named = "APP_TEST_DB_URL", matches = "jdbc:postgresql:.+")
class SchemaRulesPostgresTests {

    private static final String RUNTIME_ROLE = "planna_runtime";

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password());
    }

    @BeforeAll
    static void migrateAndCreateRuntimeRole() throws Exception {
        Flyway.configure().dataSource(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password())
                .schemas("plannasaas").defaultSchema("plannasaas").locations("classpath:db/migration").load().migrate();
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '%1$s') THEN
                            CREATE ROLE %1$s NOSUPERUSER NOBYPASSRLS;
                        END IF;
                    END $$;
                    GRANT USAGE ON SCHEMA plannasaas TO %1$s;
                    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA plannasaas TO %1$s;
                    GRANT USAGE ON ALL SEQUENCES IN SCHEMA plannasaas TO %1$s;
                    GRANT %1$s TO CURRENT_USER;
                    """.formatted(RUNTIME_ROLE));
        }
    }

    @Test
    void theSchemaKeepsItsRules() throws Exception {
        String script = new ClassPathResource("db/schema-rules.sql").getContentAsString(StandardCharsets.UTF_8);
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("SET LOCAL ROLE " + RUNTIME_ROLE);
                assertThatCode(() -> s.execute(script)).doesNotThrowAnyException();
            } finally {
                c.rollback();
            }
        }
    }
}
