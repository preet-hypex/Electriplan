package com.hypex.electriplan.security;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cors")
record CorsSettings(@Nullable List<String> allowedOrigins) {}
