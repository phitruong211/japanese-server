package com.japaneselearning.japanese_learning_api.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Compression is applied by the servlet container, so MockMvc cannot observe it; this uses a real port. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=",
        "spring.datasource.url=jdbc:h2:mem:compression-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
class ResponseCompressionIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired ObjectMapper mapper;
    // The JDK client never adds Accept-Encoding and never decodes bodies, so headers are observed unchanged.
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<byte[]> send(HttpRequest.Builder builder) throws Exception {
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path));
    }

    private String registerAndGetToken() throws Exception {
        String body = """
                {"email":"gzip-%d@example.com","password":"TestPassword123!","displayName":"Gzip","deviceName":"test"}
                """.formatted(System.nanoTime());
        var response = send(request("/auth/register").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body()).get("accessToken").asString();
    }

    private String snapshotJson(int cards) {
        var items = new StringBuilder();
        for (int i = 0; i < cards; i++) {
            if (i > 0) items.append(',');
            items.append("""
                    {"cardId":"vocab-%d","deckType":"vocabulary","state":"review","easeFactor":2.5,"intervalDays":12.0,\
                    "dueDate":"2026-10-30T00:11:40Z","reps":5,"lapses":0,"lastReviewedAt":"2026-10-17T00:00:00Z"}
                    """.formatted(i));
        }
        return "{\"revision\":0,\"bookmarks\":[],\"srsCards\":[" + items + "],\"studyDays\":[]}";
    }

    @Test
    void largeJsonIsGzippedOnlyWhenRequestedAndDecodesToTheSameSnapshot() throws Exception {
        String token = registerAndGetToken();
        var put = send(request("/users/me/learning").header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(snapshotJson(300))));
        assertThat(put.statusCode()).isEqualTo(200);

        var plain = send(request("/users/me/learning").header("Authorization", "Bearer " + token).GET());
        assertThat(plain.headers().firstValue("Content-Encoding")).isEmpty();

        var gzipped = send(request("/users/me/learning").header("Authorization", "Bearer " + token)
                .header("Accept-Encoding", "gzip").GET());
        assertThat(gzipped.headers().firstValue("Content-Encoding")).contains("gzip");
        assertThat(gzipped.body().length).isLessThan(plain.body().length / 5);

        try (var in = new GZIPInputStream(new ByteArrayInputStream(gzipped.body()))) {
            JsonNode decoded = mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(decoded).isEqualTo(mapper.readTree(plain.body()));
            assertThat(decoded.get("srsCards")).hasSize(300);
        }
    }

    @Test
    void smallResponsesStayUncompressed() throws Exception {
        String token = registerAndGetToken();
        var response = send(request("/auth/me").header("Authorization", "Bearer " + token)
                .header("Accept-Encoding", "gzip").GET());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Encoding")).isEmpty();
    }
}
