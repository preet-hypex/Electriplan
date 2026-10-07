package com.hypex.electriplan.users;

import com.hypex.electriplan.TestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "APP_TEST_DB_URL", matches = "jdbc:postgresql:.+")
class SupabaseUserPostgresTests {

    private static final UUID ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final Instant CREATED = Instant.parse("2026-09-30T00:00:00.123456Z");

    private static JdbcClient db;
    private static SupabaseUserRepository copies;

    @BeforeAll
    static void migrate() {
        DataSource source = new DriverManagerDataSource(System.getenv("APP_TEST_DB_URL"), TestDatabase.user(), TestDatabase.password());
        Flyway.configure().dataSource(source).schemas("electriplan").defaultSchema("electriplan")
                .locations("classpath:db/migration").load().migrate();
        db = JdbcClient.create(source);
        copies = new SupabaseUserRepository(db, new ObjectMapper());
    }

    @AfterEach
    void leaveNothingBehind() {
        db.sql("delete from electriplan.supabase_user where id::text like 'aaaaaaaa-%'").update();
    }

    private static AdminUser sam(String name, Instant lastSignIn) {
        return new AdminUser(ID, "sam@example.com", null, Map.of("full_name", name), Map.of("provider", "email"),
                CREATED, null, lastSignIn, null, CREATED, lastSignIn);
    }

    @Test
    void copiesEverySupabaseFieldAndReadsItBackUnchanged() {
        Instant signedIn = Instant.parse("2026-10-05T08:00:00Z");
        assertThat(copies.upsert(sam("Sam Lee", signedIn))).isTrue();

        SupabaseUser copy = copies.find(ID).orElseThrow();
        assertThat(copy.email()).isEqualTo("sam@example.com");
        assertThat(copy.userMetadata()).isEqualTo(Map.of("full_name", "Sam Lee"));
        assertThat(copy.appMetadata()).isEqualTo(Map.of("provider", "email"));
        assertThat(copy.createdAt()).isEqualTo(CREATED);
        assertThat(copy.lastSignInAt()).isEqualTo(signedIn);
        assertThat(copy.copiedAt()).isNotNull();
        assertThat(copies.exists(ID)).isTrue();
    }

    @Test
    void writesOnlyWhenSupabaseChangedSomething() {
        Instant signedIn = Instant.parse("2026-10-05T08:00:00Z");
        copies.upsert(sam("Sam Lee", signedIn));

        assertThat(copies.upsert(sam("Sam Lee", signedIn))).as("same values").isFalse();
        assertThat(copies.upsert(sam("Samantha Lee", signedIn))).as("new name").isTrue();
        assertThat(copies.find(ID).orElseThrow().userMetadata()).containsEntry("full_name", "Samantha Lee");
    }

    @Test
    void removesTheCopyOfADeletedUser() {
        copies.upsert(sam("Sam Lee", CREATED));

        assertThat(copies.delete(ID)).isTrue();
        assertThat(copies.find(ID)).isEmpty();
        assertThat(copies.ids()).doesNotContain(ID);
    }
}
