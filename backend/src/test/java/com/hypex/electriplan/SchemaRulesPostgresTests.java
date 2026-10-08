package com.hypex.electriplan;

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
 * <p>They run as the API's own login (electriplan_api, see migration V4), never
 * as the owner: a superuser bypasses row-level security. Everything happens in
 * one transaction that is rolled back.
 */
@EnabledIfEnvironmentVariable(named = "APP_TEST_DB_URL", matches = "jdbc:postgresql:.+")
class SchemaRulesPostgresTests {

    @BeforeAll
    static void migrate() {
        Flyway.configure().dataSource(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password())
                .schemas("electriplan").defaultSchema("electriplan").locations("classpath:db/migration").load().migrate();
    }

    @Test
    void theSchemaKeepsItsRules() throws Exception {
        String script = new ClassPathResource("db/schema-rules.sql").getContentAsString(StandardCharsets.UTF_8);
        // As the API's own login (electriplan_api): the real privileges, with
        // row-level security applying as it does in production.
        try (Connection c = DriverManager.getConnection(System.getenv("APP_TEST_DB_URL"), TestDatabase.apiUser(), TestDatabase.apiPassword())) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                assertThatCode(() -> s.execute(script)).doesNotThrowAnyException();
            } finally {
                c.rollback();
            }
        }
    }
}
