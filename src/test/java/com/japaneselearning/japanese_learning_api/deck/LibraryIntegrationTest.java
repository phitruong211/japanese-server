package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import com.japaneselearning.japanese_learning_api.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:library-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class LibraryIntegrationTest extends com.japaneselearning.japanese_learning_api.MySqlVerification {
    @Autowired MockMvc mvc;
    @Autowired DeckService service;
    @Autowired DeckShareService sharing;
    @Autowired DeckGroupService groupService;
    @Autowired DeckGroupShareService groupSharing;
    @Autowired UserRepository users;
    @Autowired DeckRepository decks;
    @Autowired JwtService jwt;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    /** The library is one global list, so each test starts with nothing published. */
    @org.junit.jupiter.api.BeforeEach void emptyLibrary() {
        new TransactionTemplate(transactions).executeWithoutResult(s -> decks.findAll().forEach(d -> { d.setVisibility(DomainTypes.Visibility.PRIVATE); d.setPublishedAt(null); }));
    }

    private User user(String name, DomainTypes.Role role) {
        var u = new User(); u.setId(UUID.randomUUID()); u.setEmail(u.getId() + "@lib.test"); u.setDisplayName(name); u.setPasswordHash("test"); u.setRole(role);
        return users.saveAndFlush(u);
    }
    private User user(String name) { return user(name, DomainTypes.Role.USER); }
    private String bearer(User u) { return "Bearer " + jwt.createAccessToken(u); }
    private static RequestPostProcessor from(String ip) { return r -> { r.setRemoteAddr(ip); return r; }; }
    private String ip() { return "10." + new Random().nextInt(250) + "." + new Random().nextInt(250) + "." + (1 + new Random().nextInt(250)); }

    private DeckDtos.DeckResponse deck(User owner, String name, int cards, DomainTypes.SourceType type) {
        var requests = java.util.stream.IntStream.range(0, cards).mapToObj(i -> new DeckDtos.CardRequest(name + " " + i, "nghĩa " + i, null, null, DomainTypes.CardKind.VOCABULARY, i, null, null, null)).toList();
        return service.create(owner.getId(), new DeckDtos.CreateDeckRequest(name, null, type, "f.csv", "CSV", null, null, requests, type.name(), null, null, List.of()));
    }
    private DeckDtos.DeckResponse deck(User owner, String name) { return deck(owner, name, 3, DomainTypes.SourceType.IMPORT); }
    private DeckShareService.Link publish(User owner, UUID deckId, String level, String description) {
        return new LibraryService_(owner, deckId, level, description).run();
    }
    /** Calls the real endpoint so the controller, security and service are all in the path. */
    private class LibraryService_ {
        final User owner; final UUID id; final String level, description;
        LibraryService_(User owner, UUID id, String level, String description) { this.owner = owner; this.id = id; this.level = level; this.description = description; }
        DeckShareService.Link run() {
            try {
                var body = "{\"published\":true,\"level\":" + (level == null ? "null" : "\"" + level + "\"") + ",\"description\":" + (description == null ? "null" : "\"" + description + "\"") + "}";
                var result = mvc.perform(put("/api/v1/decks/" + id + "/library").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
                return sharing.status(owner.getId(), id);
            } catch (Exception e) { throw new RuntimeException(e); }
        }
    }
    private int publishStatus(User owner, UUID id, String body) throws Exception {
        return mvc.perform(put("/api/v1/decks/" + id + "/library").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }
    private String libraryJson(String query) throws Exception {
        return mvc.perform(get("/api/v1/library" + query).with(from(ip()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    /** For text that needs encoding: the template variable is encoded once, unlike a literal "%C3" in the URL. */
    private String search(String text) throws Exception {
        return mvc.perform(get("/api/v1/library?query={q}", text).with(from(ip()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void shortCodesResolveToTheLinkAndDieWithIt() throws Exception {
        var owner = user("Chủ"); var source = deck(owner, "Bộ mã");
        var link = sharing.enable(owner.getId(), source.deck().id());
        assertThat(link.code()).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
        String typed = link.code().toLowerCase().replace("-", " - ");
        mvc.perform(get("/api/v1/share-codes/{code}", typed).with(from(ip()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("deck")).andExpect(jsonPath("$.token").value(link.token()))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/v1/share-codes/ABCD-EFGH").with(from(ip()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/share-codes/not-a-code").with(from(ip()))).andExpect(status().isNotFound());
        assertThat(sharing.enable(owner.getId(), source.deck().id()).code()).isEqualTo(link.code());
        sharing.revoke(owner.getId(), source.deck().id());
        mvc.perform(get("/api/v1/share-codes/" + link.code()).with(from(ip()))).andExpect(status().isNotFound());
        assertThat(sharing.enable(owner.getId(), source.deck().id()).code()).isNotEqualTo(link.code());
    }

    @Test void groupsGetCodesToo() throws Exception {
        var owner = user("Chủ nhóm"); var source = deck(owner, "Trong nhóm");
        groupService.create(owner.getId(), new DeckGroupService.NameRequest("Nhóm", null));
        var group = groupService.list(owner.getId()).getFirst();
        groupService.move(owner.getId(), source.deck().id(), new DeckGroupService.MoveRequest(group.id(), null, source.deck().version()));
        var link = groupSharing.enable(owner.getId(), group.id());
        assertThat(link.code()).matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
        mvc.perform(get("/api/v1/share-codes/" + link.code()).with(from(ip()))).andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("group")).andExpect(jsonPath("$.token").value(link.token()));
    }

    @Test void linksAndCodesExpireAndRenewing() throws Exception {
        var owner = user("Chủ hạn"); var recipient = user("Người nhận"); var source = deck(owner, "Có hạn");
        assertThatThrownBy(() -> sharing.enable(owner.getId(), source.deck().id(), new DeckShareService.ShareOptions(false, 5))).isInstanceOf(ApiException.class);
        var link = sharing.enable(owner.getId(), source.deck().id(), new DeckShareService.ShareOptions(false, 7));
        assertThat(link.expiresAt()).isBetween(Instant.now().plus(6, ChronoUnit.DAYS), Instant.now().plus(8, ChronoUnit.DAYS));
        mvc.perform(get("/api/v1/shared-decks/" + link.token())).andExpect(status().isOk());
        new TransactionTemplate(transactions).executeWithoutResult(s -> decks.findById(source.deck().id()).orElseThrow().setShareExpiresAt(Instant.now().minusSeconds(5)));
        mvc.perform(get("/api/v1/shared-decks/" + link.token())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/share-codes/" + link.code()).with(from(ip()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/shared-decks/" + link.token() + "/copy").header("Authorization", bearer(recipient))).andExpect(status().isNotFound());
        // The owner renewing the link brings it back with the same token and code.
        var renewed = sharing.enable(owner.getId(), source.deck().id());
        assertThat(renewed.expiresAt()).isNull();
        assertThat(renewed.token()).isEqualTo(link.token());
        mvc.perform(get("/api/v1/shared-decks/" + link.token())).andExpect(status().isOk());
    }

    @Test void codeLookupsAreRateLimitedPerClient() throws Exception {
        String client = ip();
        for (int i = 0; i < 20; i++) mvc.perform(get("/api/v1/share-codes/ABCD-EFGH").with(from(client))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/share-codes/ABCD-EFGH").with(from(client))).andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/v1/share-codes/ABCD-EFGH").with(from(ip()))).andExpect(status().isNotFound());
    }

    @Test void publishingListsTheDeckAndUnpublishingOrRevokingRemovesIt() throws Exception {
        var owner = user("Cô Lan"); var source = deck(owner, "Từ vựng N3 bài 1");
        publish(owner, source.deck().id(), "N3", "Từ vựng bài 1 giáo trình Minna");
        var json = libraryJson("");
        assertThat((List<?>) JsonPath.read(json, "$.items")).hasSize(1);
        assertThat(JsonPath.<String>read(json, "$.items[0].author")).isEqualTo("Cô Lan");
        assertThat(JsonPath.<String>read(json, "$.items[0].level")).isEqualTo("N3");
        assertThat(JsonPath.<Integer>read(json, "$.items[0].cardCount")).isEqualTo(3);
        assertThat(json).doesNotContain(owner.getEmail()).doesNotContain(owner.getId().toString()).doesNotContain(source.deck().id().toString());
        assertThat(sharing.status(owner.getId(), source.deck().id()).expiresAt()).isNull();
        // The listed token opens the ordinary shared page.
        mvc.perform(get("/api/v1/shared-decks/" + JsonPath.<String>read(json, "$.items[0].token"))).andExpect(status().isOk());
        assertThat((List<?>) JsonPath.read(libraryJson("?query=minna"), "$.items")).hasSize(1);
        assertThat((List<?>) JsonPath.read(search("cô lan"), "$.items")).hasSize(1);
        assertThat((List<?>) JsonPath.read(libraryJson("?query=zzz"), "$.items")).isEmpty();
        assertThat((List<?>) JsonPath.read(libraryJson("?level=N3"), "$.items")).hasSize(1);
        assertThat((List<?>) JsonPath.read(libraryJson("?level=N1"), "$.items")).isEmpty();
        assertThat(mvc.perform(get("/api/v1/library?level=N9").with(from(ip()))).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat((List<?>) JsonPath.read(search("%"), "$.items")).as("a literal % must not act as a wildcard").isEmpty();
        assertThat((List<?>) JsonPath.read(search("_"), "$.items")).as("nor a literal _").isEmpty();

        mvc.perform(put("/api/v1/decks/" + source.deck().id() + "/library").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"published\":false}")).andExpect(status().isOk());
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).isEmpty();
        assertThat(sharing.status(owner.getId(), source.deck().id()).token()).isNotNull();
        publish(owner, source.deck().id(), null, null);
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).hasSize(1);
        sharing.revoke(owner.getId(), source.deck().id());
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).isEmpty();
        assertThat(sharing.status(owner.getId(), source.deck().id()).library().published()).isFalse();
    }

    @Test void publishingHasRulesAndCannotBeBypassed() throws Exception {
        var owner = user("Chủ luật"); var stranger = user("Người lạ");
        var manual = deck(owner, "Thủ công", 2, DomainTypes.SourceType.MANUAL);
        var empty = deck(owner, "Trống", 0, DomainTypes.SourceType.IMPORT);
        var ok = deck(owner, "Hợp lệ");
        assertThat(publishStatus(owner, manual.deck().id(), "{\"published\":true}")).isEqualTo(400);
        assertThat(publishStatus(owner, empty.deck().id(), "{\"published\":true}")).isEqualTo(400);
        assertThat(publishStatus(owner, ok.deck().id(), "{\"published\":true,\"level\":\"N9\"}")).isEqualTo(400);
        assertThat(publishStatus(owner, ok.deck().id(), "{\"published\":true,\"description\":\"" + "x".repeat(501) + "\"}")).isEqualTo(400);
        assertThat(publishStatus(stranger, ok.deck().id(), "{\"published\":true}")).isEqualTo(404);
        mvc.perform(put("/api/v1/decks/" + ok.deck().id() + "/library").contentType(MediaType.APPLICATION_JSON).content("{\"published\":true}")).andExpect(status().isUnauthorized());
        // Setting visibility through the ordinary update would skip every rule above.
        assertThatThrownBy(() -> service.update(owner.getId(), ok.deck().id(), new DeckDtos.UpdateDeckRequest(null, null, DomainTypes.Visibility.PUBLIC, null))).isInstanceOf(ApiException.class);
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).isEmpty();
    }

    @Test void thereIsADailyPublishingLimit() throws Exception {
        var owner = user("Đăng nhiều");
        for (int i = 0; i < LibraryService.MAX_PUBLISH_PER_DAY; i++) assertThat(publishStatus(owner, deck(owner, "Bộ " + i).deck().id(), "{\"published\":true}")).isEqualTo(200);
        var sixth = deck(owner, "Bộ thứ sáu");
        assertThat(publishStatus(owner, sixth.deck().id(), "{\"published\":true}")).isEqualTo(429);
        // Editing an already published deck is not a new post.
        var firstId = decks.findAllByOwnerIdAndDeletedAtIsNullOrderByPositionAscUpdatedAtDesc(owner.getId()).stream().filter(d -> d.getVisibility() == DomainTypes.Visibility.PUBLIC).findFirst().orElseThrow().getId();
        assertThat(publishStatus(owner, firstId, "{\"published\":true,\"level\":\"N2\"}")).isEqualTo(200);
    }

    @Test void downloadsCountDistinctSavesOfPublishedDecks() throws Exception {
        var owner = user("Chủ tải"); var a = user("A"); var b = user("B");
        var source = deck(owner, "Được tải");
        publish(owner, source.deck().id(), "N4", null);
        String token = sharing.status(owner.getId(), source.deck().id()).token();
        mvc.perform(post("/api/v1/shared-decks/" + token + "/copy").header("Authorization", bearer(a))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/shared-decks/" + token + "/copy").header("Authorization", bearer(a))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/shared-decks/" + token + "/copy").header("Authorization", bearer(owner))).andExpect(status().isOk());
        assertThat(JsonPath.<Integer>read(libraryJson(""), "$.items[0].downloads")).isEqualTo(1);
        mvc.perform(post("/api/v1/shared-decks/" + token + "/copy").header("Authorization", bearer(b))).andExpect(status().isOk());
        assertThat(JsonPath.<Integer>read(libraryJson(""), "$.items[0].downloads")).isEqualTo(2);
        assertThat(sharing.status(owner.getId(), source.deck().id()).library().downloads()).isEqualTo(2);
        var other = deck(owner, "Ít người tải");
        publish(owner, other.deck().id(), "N4", null);
        assertThat(JsonPath.<String>read(libraryJson("?sort=popular"), "$.items[0].name")).isEqualTo("Được tải");
        assertThat(JsonPath.<String>read(libraryJson("?sort=recent"), "$.items[0].name")).isEqualTo("Ít người tải");
    }

    @Test void reportsAreValidatedLimitedAndHideRepeatedlyReportedDecks() throws Exception {
        var owner = user("Bị báo cáo"); var source = deck(owner, "Nghi ngờ");
        publish(owner, source.deck().id(), "N5", null);
        String token = sharing.status(owner.getId(), source.deck().id()).token();
        var reporters = List.of(user("R1"), user("R2"), user("R3"));
        String body = "{\"reason\":\"COPYRIGHT\",\"details\":\"Chép từ sách\"}";
        mvc.perform(post("/api/v1/shared-decks/" + token + "/report").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(reporters.get(0))).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"NOPE\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(reporters.get(0))).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/shared-decks/not-a-token/report").header("Authorization", bearer(reporters.get(0))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(reporters.get(i))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(reporters.get(0))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).hasSize(1);
        mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(reporters.get(2))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        // Three open reports: hidden from the library, the link still works for people who already have it.
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).isEmpty();
        mvc.perform(get("/api/v1/shared-decks/" + token)).andExpect(status().isOk());
        assertThat(sharing.status(owner.getId(), source.deck().id()).library().moderation()).isEqualTo("UNDER_REVIEW");
    }

    @Test void aUserCannotFileMoreThanTenReportsADay() throws Exception {
        var reporter = user("Hay báo cáo");
        for (int i = 0; i < LibraryService.MAX_REPORTS_PER_DAY; i++) {
            var owner = user("Chủ " + i); var d = deck(owner, "Bộ " + i);
            mvc.perform(post("/api/v1/shared-decks/" + sharing.enable(owner.getId(), d.deck().id()).token() + "/report").header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}")).andExpect(status().isNoContent());
        }
        var owner = user("Chủ cuối"); var d = deck(owner, "Bộ cuối");
        mvc.perform(post("/api/v1/shared-decks/" + sharing.enable(owner.getId(), d.deck().id()).token() + "/report").header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}")).andExpect(status().isTooManyRequests());
    }

    @Test void onlyAdminsModerateAndTakedownsAreFinalForTheLinkButNotForCopies() throws Exception {
        var admin = user("Quản trị", DomainTypes.Role.ADMIN); var owner = user("Chủ vi phạm"); var reporter = user("Người báo"); var saver = user("Người đã lưu");
        var source = deck(owner, "Vi phạm bản quyền");
        publish(owner, source.deck().id(), "N2", null);
        String token = sharing.status(owner.getId(), source.deck().id()).token();
        String savedId = JsonPath.read(mvc.perform(post("/api/v1/shared-decks/" + token + "/copy").header("Authorization", bearer(saver))).andReturn().getResponse().getContentAsString(), "$.deckId");
        mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(reporter)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"COPYRIGHT\",\"details\":\"Sách có bản quyền\"}")).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/admin/reports").header("Authorization", bearer(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
        var listing = mvc.perform(get("/api/v1/admin/reports").header("Authorization", bearer(admin))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(listing, "$.reports[0].deckName")).isEqualTo("Vi phạm bản quyền");
        assertThat(JsonPath.<String>read(listing, "$.reports[0].reporter")).isEqualTo("Người báo");
        assertThat(JsonPath.<String>read(listing, "$.reports[0].deckToken")).isEqualTo(token);
        String deckId = JsonPath.read(listing, "$.reports[0].deckId");

        mvc.perform(post("/api/v1/admin/decks/" + deckId + "/remove").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/decks/" + deckId + "/remove").header("Authorization", bearer(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/decks/" + deckId + "/remove").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Vi phạm bản quyền sách giáo trình\"}")).andExpect(status().isNoContent());

        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).isEmpty();
        mvc.perform(get("/api/v1/shared-decks/" + token)).andExpect(status().isNotFound());
        var status = sharing.status(owner.getId(), source.deck().id());
        assertThat(status.token()).isNull();
        assertThat(status.library().moderation()).isEqualTo("REMOVED");
        assertThat(status.library().removedReason()).contains("bản quyền");
        assertThatThrownBy(() -> sharing.enable(owner.getId(), source.deck().id())).isInstanceOf(ApiException.class).hasMessageContaining("bản quyền");
        assertThat(publishStatus(owner, source.deck().id(), "{\"published\":true}")).isEqualTo(409);
        // A copy someone already saved belongs to them.
        assertThat(service.get(saver.getId(), UUID.fromString(savedId)).cards()).hasSize(3);
        assertThat(JsonPath.<List<?>>read(mvc.perform(get("/api/v1/admin/reports?status=ACTIONED").header("Authorization", bearer(admin))).andReturn().getResponse().getContentAsString(), "$.reports")).hasSize(1);
        assertThat(JsonPath.<List<?>>read(mvc.perform(get("/api/v1/admin/reports").header("Authorization", bearer(admin))).andReturn().getResponse().getContentAsString(), "$.reports")).isEmpty();

        mvc.perform(post("/api/v1/admin/decks/" + deckId + "/restore").header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        assertThat(sharing.enable(owner.getId(), source.deck().id()).token()).isNotNull();
    }

    @Test void dismissingReportsClearsTheAutomaticHold() throws Exception {
        var admin = user("Admin 2", DomainTypes.Role.ADMIN); var owner = user("Chủ oan"); var source = deck(owner, "Bị báo nhầm");
        publish(owner, source.deck().id(), null, null);
        String token = sharing.status(owner.getId(), source.deck().id()).token();
        for (int i = 0; i < 3; i++) mvc.perform(post("/api/v1/shared-decks/" + token + "/report").header("Authorization", bearer(user("R" + i))).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}")).andExpect(status().isNoContent());
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).isEmpty();
        var reports = JsonPath.<List<String>>read(mvc.perform(get("/api/v1/admin/reports").header("Authorization", bearer(admin))).andReturn().getResponse().getContentAsString(), "$.reports[?(@.deckName=='Bị báo nhầm')].id");
        assertThat(reports).hasSize(3);
        for (String id : reports) mvc.perform(post("/api/v1/admin/reports/" + id + "/dismiss").header("Authorization", bearer(admin))).andExpect(status().isNoContent());
        assertThat((List<?>) JsonPath.read(libraryJson(""), "$.items")).hasSize(1);
        assertThat(sharing.status(owner.getId(), source.deck().id()).library().moderation()).isEqualTo("ACTIVE");
    }
}
