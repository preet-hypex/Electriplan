package com.hypex.planna;

/**
 * Credentials for the Postgres tests, which run when APP_TEST_DB_URL is set.
 * APP_TEST_DB_USER and APP_TEST_DB_PASSWORD override the local defaults.
 */
public final class TestDatabase {

    private TestDatabase() {
    }

    public static String user() {
        return env("APP_TEST_DB_USER", "plannasaas");
    }

    public static String password() {
        return env("APP_TEST_DB_PASSWORD", "plannasaas");
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
