package com.japaneselearning.japanese_learning_api.font;

import com.japaneselearning.japanese_learning_api.model.User;
import com.japaneselearning.japanese_learning_api.repository.UserRepository;
import com.japaneselearning.japanese_learning_api.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:font-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class FontIntegrationTest extends com.japaneselearning.japanese_learning_api.MySqlVerification {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    private User user() {
        var u = new User(); u.setId(UUID.randomUUID()); u.setEmail(u.getId() + "@font.test"); u.setDisplayName("Người dùng font"); u.setPasswordHash("test");
        return users.saveAndFlush(u);
    }
    private String bearer(User u) { return "Bearer " + jwt.createAccessToken(u); }
    private MvcResult upload(User u, String filename, byte[] data, String name, int status) throws Exception {
        var request = multipart("/api/v1/fonts").file(new MockMultipartFile("file", filename, "application/octet-stream", data)).header("Authorization", bearer(u));
        if (name != null) request.param("name", name);
        return mvc.perform(request).andExpect(status().is(status)).andReturn();
    }
    private static String id(MvcResult result) throws Exception { return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id"); }

    @Test void uploadListDownloadAndDeleteStayPrivateToTheOwner() throws Exception {
        var owner = user(); var stranger = user();
        var data = FontTestData.woff2(1, 800);
        var created = upload(owner, "Klee Custom.woff2", data, "  Chữ   viết\ttay ", 201);
        String id = id(created);
        mvc.perform(get("/api/v1/fonts").header("Authorization", bearer(owner))).andExpect(status().isOk())
            .andExpect(jsonPath("$.fonts.length()").value(1)).andExpect(jsonPath("$.fonts[0].name").value("Chữ viết tay"))
            .andExpect(jsonPath("$.fonts[0].format").value("woff2")).andExpect(jsonPath("$.limits.maxFonts").value(10))
            .andExpect(jsonPath("$.limits.usedBytes").value(data.length));
        mvc.perform(get("/api/v1/fonts/" + id + "/file").header("Authorization", bearer(owner))).andExpect(status().isOk())
            .andExpect(content().contentType("font/woff2")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(content().bytes(data));
        mvc.perform(get("/api/v1/fonts/" + id + "/file").header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/fonts").header("Authorization", bearer(stranger))).andExpect(jsonPath("$.fonts.length()").value(0));
        mvc.perform(delete("/api/v1/fonts/" + id).header("Authorization", bearer(stranger))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/fonts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/fonts/" + id + "/file")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/fonts/" + id).header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/fonts/" + id + "/file").header("Authorization", bearer(owner))).andExpect(status().isNotFound());
    }

    @Test void uploadingTheSameFontAgainReturnsTheExistingOne() throws Exception {
        var owner = user();
        var first = id(upload(owner, "a.ttf", FontTestData.ttf(7, 500), "A", 201));
        var again = upload(owner, "renamed.ttf", FontTestData.ttf(7, 500), "B", 200);
        assertThat(id(again)).isEqualTo(first);
        mvc.perform(get("/api/v1/fonts").header("Authorization", bearer(owner))).andExpect(jsonPath("$.fonts.length()").value(1)).andExpect(jsonPath("$.fonts[0].name").value("A"));
        var other = user();
        assertThat(id(upload(other, "a.ttf", FontTestData.ttf(7, 500), "A", 201))).isNotEqualTo(first);
    }

    @Test void rejectsFilesThatAreNotFontsWithoutStoringThem() throws Exception {
        var owner = user();
        upload(owner, "fake.ttf", "just text".repeat(100).getBytes(), "x", 400);
        upload(owner, "a.otf", FontTestData.ttf(1, 400), "x", 400);
        upload(owner, "a.woff2", FontTestData.ttf(1, 400), "x", 400);
        mvc.perform(multipart("/api/v1/fonts").header("Authorization", bearer(owner))).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/fonts").header("Authorization", bearer(owner))).andExpect(jsonPath("$.fonts.length()").value(0));
    }

    // MockMvc does not apply the servlet container's multipart limit (checked against the real server separately),
    // so this exercises the validator's own cap.
    @Test void aSingleFileOverTheLimitIsRefused() throws Exception {
        upload(user(), "huge.ttf", new byte[5 * 1024 * 1024 + 512 * 1024], "x", 400);
    }

    @Test void aUserCanKeepTenFontsAndNoMore() throws Exception {
        var owner = user();
        for (int i = 0; i < FontService.MAX_FONTS; i++) upload(owner, "f" + i + ".ttf", FontTestData.ttf(100 + i, 400), "Font " + i, 201);
        upload(owner, "extra.ttf", FontTestData.ttf(999, 400), "Extra", 409);
        var first = com.jayway.jsonpath.JsonPath.<String>read(mvc.perform(get("/api/v1/fonts").header("Authorization", bearer(owner))).andReturn().getResponse().getContentAsString(), "$.fonts[0].id");
        mvc.perform(delete("/api/v1/fonts/" + first).header("Authorization", bearer(owner))).andExpect(status().isNoContent());
        upload(owner, "extra.ttf", FontTestData.ttf(999, 400), "Extra", 201);
    }

    @Test void totalStorageIsCapped() throws Exception {
        var owner = user();
        int each = 3 * 1024 * 1024;
        for (int i = 0; i < 8; i++) upload(owner, "big" + i + ".ttf", FontTestData.ttf(200 + i, each), "Big " + i, 201);
        upload(owner, "big8.ttf", FontTestData.ttf(300, each), "Big 8", 409);
        mvc.perform(get("/api/v1/fonts").header("Authorization", bearer(owner))).andExpect(jsonPath("$.fonts.length()").value(8));
        // Another user is unaffected.
        upload(user(), "big8.ttf", FontTestData.ttf(300, each), "Big", 201);
    }

    @Test void namesAreCleanedAndDefaultToTheFileName() throws Exception {
        var owner = user();
        upload(owner, "Zen Antique.woff2", FontTestData.woff2(5, 700), null, 201);
        upload(owner, "b.woff2", FontTestData.woff2(6, 700), "x".repeat(200), 201);
        var body = mvc.perform(get("/api/v1/fonts").header("Authorization", bearer(owner))).andReturn().getResponse().getContentAsString();
        assertThat(com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(body, "$.fonts[*].name")).contains("Zen Antique").anyMatch(n -> n.length() == 60);
    }
}
