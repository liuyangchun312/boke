package com.example.blog.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTests {
    @Test
    void rejectsDevProfileInProduction() {
        MockEnvironment environment = validProductionEnvironment();
        environment.setActiveProfiles("prod", "dev", "mysql");

        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev");
    }

    @Test
    void rejectsMissingOrDevelopmentCredentialsAndJwtSettings() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("blog.jwt.secret", "local-development-secret-change-me-32-bytes-minimum")
                .withProperty("blog.jwt.expiration-minutes", "0")
                .withProperty("blog.bootstrap.username", "admin")
                .withProperty("blog.bootstrap.password", "admin123")
                .withProperty("blog.bootstrap.display-name", "Admin")
                .withProperty("blog.bootstrap.role", "ADMIN")
                .withProperty("spring.datasource.url", "jdbc:h2:mem:blog");
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production configuration invalid")
                .hasMessageContaining("JWT")
                .hasMessageContaining("bootstrap")
                .hasMessageContaining("datasource");
    }

    @Test
    void acceptsStrongJwtBootstrapAndMysqlProfile() {
        assertThatCode(() -> ProductionConfigurationValidator.validate(validProductionEnvironment()))
                .doesNotThrowAnyException();
    }

    private MockEnvironment validProductionEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("blog.jwt.secret", "this-is-a-strong-production-key-1234567890")
                .withProperty("blog.jwt.expiration-minutes", "60")
                .withProperty("blog.bootstrap.username", "site-owner")
                .withProperty("blog.bootstrap.password", "a-long-production-password")
                .withProperty("blog.bootstrap.display-name", "Site Owner")
                .withProperty("blog.bootstrap.role", "ADMIN")
                .withProperty("spring.datasource.url", "jdbc:mysql://db:3306/blog")
                .withProperty("spring.datasource.username", "blog")
                .withProperty("spring.datasource.password", "database-password");
        environment.setActiveProfiles("prod", "mysql");
        return environment;
    }
}
