package com.hypex.electriplan;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The whole application against a real Postgres, set up as in production: the
 * API works as its own login (electriplan_api) with row-level security
 * applying, Flyway migrates as the owner, Hibernate validates every entity
 * against the migrated tables, and each transaction carries the request's
 * company. Runs when APP_TEST_DB_URL is set.
 */
@EnabledIfEnvironmentVariable(named = "APP_TEST_DB_URL", matches = "jdbc:postgresql:.+")
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresApplicationTest {

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("APP_TEST_DB_URL"));
        registry.add("spring.datasource.username", TestDatabase::apiUser);
        registry.add("spring.datasource.password", TestDatabase::apiPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.user", TestDatabase::user);
        registry.add("spring.flyway.password", TestDatabase::password);
        registry.add("spring.flyway.schemas", () -> "electriplan");
        registry.add("spring.flyway.default-schema", () -> "electriplan");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("electriplan.tenancy.session-settings", () -> "true");
    }
}
