package com.japaneselearning.japanese_learning_api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Lets an integration test run against a disposable real MySQL instead of H2: pass -Dsharing.mysql.url=jdbc:mysql://…
 * (the same switch DeckShareIntegrationTest has always used). That applies every Flyway migration and makes Hibernate
 * validate the entities against the resulting schema, which H2 with generated tables cannot check.
 */
public abstract class MySqlVerification {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String url = System.getProperty("sharing.mysql.url");
        if (url == null || url.isBlank()) return;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> System.getProperty("sharing.mysql.username", "root"));
        registry.add("spring.datasource.password", () -> System.getProperty("sharing.mysql.password", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
