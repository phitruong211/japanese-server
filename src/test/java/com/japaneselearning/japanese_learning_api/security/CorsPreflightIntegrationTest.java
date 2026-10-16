package com.japaneselearning.japanese_learning_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:cors-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "app.cors.allowed-origins=https://app.example.com"
})
@AutoConfigureMockMvc
class CorsPreflightIntegrationTest {
    @Autowired MockMvc mockMvc;

    @Test
    void preflightIsCachedByTheBrowserForAnHour() throws Exception {
        mockMvc.perform(options("/api/v1/users/me/learning")
                        .header("Origin", "https://app.example.com")
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    void unknownOriginIsStillRejected() throws Exception {
        mockMvc.perform(options("/api/v1/users/me/learning")
                        .header("Origin", "https://evil.example.org")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
    }
}
