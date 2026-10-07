package com.hypex.planna.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SupabaseSettingsTests {

    @Test
    void verifiesTokensAgainstTheProjectsAuthIssuerAndJwks() {
        SupabaseSettings settings = new SupabaseSettings("https://abc.supabase.co/", "sb_secret_abc");
        assertThat(settings.issuer()).isEqualTo("https://abc.supabase.co/auth/v1");
        assertThat(settings.jwksUri()).isEqualTo("https://abc.supabase.co/auth/v1/.well-known/jwks.json");
    }

    @Test
    void refusesToStartWithoutAProjectUrl() {
        assertThatThrownBy(() -> new SupabaseSettings(" ", "sb_secret_abc")).hasMessageContaining("SUPABASE_URL is not set");
    }

    @Test
    void refusesToStartWithoutTheSecretKeyItCopiesUsersWith() {
        assertThatThrownBy(() -> new SupabaseSettings("https://abc.supabase.co", ""))
                .hasMessageContaining("SUPABASE_SECRET_KEY is not set");
    }

    @Test
    void refusesALegacyServiceRoleKey() {
        assertThatThrownBy(() -> new SupabaseSettings("https://abc.supabase.co", "eyJhbGciOi.legacy"))
                .hasMessageContaining("sb_secret_");
    }

    @Test
    void neverPrintsTheSecretKey() {
        assertThat(new SupabaseSettings("https://abc.supabase.co", "sb_secret_abc123").toString())
                .doesNotContain("abc123");
    }
}
