package com.hypex.electriplan.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.supabase")
public record SupabaseSettings(String url, String secretKey) {

    private static final String SECRET_KEY_PREFIX = "sb_secret_";

    public SupabaseSettings {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_URL is not set. Access tokens are verified against the project's JWKS "
                            + "endpoint, so without it every request would be refused. Run "
                            + "node scripts/setup.mjs to write backend/.env.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "SUPABASE_SECRET_KEY is not set. The API reads Supabase's users with it to keep its copy "
                            + "in Postgres. Run node scripts/setup.mjs supabase to write it to backend/.env.");
        }
        if (!secretKey.startsWith(SECRET_KEY_PREFIX)) {
            throw new IllegalStateException(
                    "SUPABASE_SECRET_KEY must be a new-style secret key starting with '" + SECRET_KEY_PREFIX
                            + "'. The legacy service_role key (a JWT beginning 'eyJ') is not accepted. "
                            + "Create one under Project settings -> API keys.");
        }
        url = url.replaceAll("/+$", "");
    }

    public String authUrl() {
        return url + "/auth/v1";
    }

    String issuer() {
        return authUrl();
    }

    String jwksUri() {
        return authUrl() + "/.well-known/jwks.json";
    }

    @Override
    public String toString() {
        return "SupabaseSettings[url=" + url + ", secretKey=" + SECRET_KEY_PREFIX + "…]";
    }
}
