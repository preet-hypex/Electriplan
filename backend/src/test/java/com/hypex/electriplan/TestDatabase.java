package com.hypex.electriplan;

/**
 * Credentials for the Postgres tests, which run when APP_TEST_DB_URL is set.
 * APP_TEST_DB_USER and APP_TEST_DB_PASSWORD override the local defaults.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    /** The owner of the schema: what Flyway migrates as. */
    public static String user() {
        return env("APP_TEST_DB_USER", "electriplan");
    }

    public static String password() {
        return env("APP_TEST_DB_PASSWORD", "electriplan");
    }

    /** The API's own login (electriplan_api): what the application connects as at runtime. */
    public static String apiUser() {
        return env("APP_TEST_DB_API_USER", "electriplan_api");
    }

    public static String apiPassword() {
        return env("APP_TEST_DB_API_PASSWORD", "electriplan_api");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
