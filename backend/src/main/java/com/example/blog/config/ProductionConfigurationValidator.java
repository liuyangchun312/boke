package com.example.blog.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProductionConfigurationValidator implements EnvironmentPostProcessor, Ordered {
    private static final String DEVELOPMENT_JWT_SECRET =
            "local-development-secret-change-me-32-bytes-minimum";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        validate(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    public static void validate(Environment environment) {
        List<String> profiles = Arrays.asList(environment.getActiveProfiles());
        if (!profiles.contains("prod")) return;

        List<String> errors = new ArrayList<>();
        if (profiles.contains("dev")) errors.add("the dev profile cannot run with prod");

        String secret = value(environment, "blog.jwt.secret");
        if (secret.isBlank() || secret.equals(DEVELOPMENT_JWT_SECRET)
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            errors.add("JWT secret must be set to a non-development value of at least 32 UTF-8 bytes");
        }
        long ttl = parseLong(environment.getProperty("blog.jwt.expiration-minutes"));
        if (ttl <= 0 || ttl > 1440) {
            errors.add("JWT expiration must be between 1 and 1440 minutes");
        }

        String username = value(environment, "blog.bootstrap.username");
        String password = value(environment, "blog.bootstrap.password");
        String displayName = value(environment, "blog.bootstrap.display-name");
        String role = value(environment, "blog.bootstrap.role");
        if (username.isBlank() || password.length() < 12 || displayName.isBlank()
                || !"ADMIN".equalsIgnoreCase(role) || "admin123".equals(password)) {
            errors.add("bootstrap administrator credentials must be complete, non-development values");
        }

        String url = value(environment, "spring.datasource.url");
        String databaseUsername = value(environment, "spring.datasource.username");
        String databasePassword = value(environment, "spring.datasource.password");
        boolean mysql = profiles.contains("mysql") && url.startsWith("jdbc:mysql:");
        boolean explicitDurable = environment.getProperty(
                "blog.production.allow-durable-datasource", Boolean.class, false)
                && !url.isBlank() && !url.startsWith("jdbc:h2:mem:");
        if ((!mysql && !explicitDurable) || databaseUsername.isBlank() || databasePassword.isBlank()) {
            errors.add("datasource must use the mysql profile or an explicitly allowed durable database with credentials");
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Production configuration invalid: " + String.join("; ", errors));
        }
    }

    private static String value(Environment environment, String key) {
        String value = environment.getProperty(key);
        return value == null ? "" : value.trim();
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value == null ? "" : value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
