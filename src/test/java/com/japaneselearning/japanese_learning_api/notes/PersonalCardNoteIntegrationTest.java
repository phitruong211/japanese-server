package com.japaneselearning.japanese_learning_api.notes;

import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.UserRepository;
import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.security.JwtService;
import com.japaneselearning.japanese_learning_api.deck.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:note-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false"
}) @AutoConfigureMockMvc @Transactional
class PersonalCardNoteIntegrationTest {
    @Autowired PersonalCardNoteService notes;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired DeckService decks;
    @Autowired DeckShareService sharing;
    @Autowired DeckGroupShareService groupSharing;
    @Autowired DeckGroupService groups;
    String key = "a".repeat(64);
    User user() { var u = new User(); u.setId(UUID.randomUUID()); u.setEmail(u.getId()+"@notes.test"); u.setPasswordHash("test"); u.setDisplayName("Notes"); return users.saveAndFlush(u); }
    @Test void notesSurviveEditsAndDeletionCannotBeResurrectedByStaleDevice() {
        var a = user(); var b = user();
        var first = notes.put(a.getId(), key, new PersonalCardNoteService.Update("水 khác お湯", 0));
        assertThat(first.version()).isEqualTo(1); assertThat(notes.get(b.getId(), key).text()).isEmpty();
        assertThatThrownBy(() -> notes.put(a.getId(), key, new PersonalCardNoteService.Update("stale", 0))).isInstanceOf(ApiException.class);
        assertThat(notes.put(a.getId(), key, new PersonalCardNoteService.Update(first.text(), 0)).version()).isEqualTo(1);
        notes.put(a.getId(), key, new PersonalCardNoteService.Update("", 1));
        assertThat(notes.get(a.getId(), key).version()).isEqualTo(2);
        assertThatThrownBy(() -> notes.put(a.getId(), key, new PersonalCardNoteService.Update("old text", 1))).isInstanceOf(ApiException.class);
    }
    @Test void apiRequiresAuthenticationValidatesLengthAndIsolatesAccounts() throws Exception {
        var owner = user(); String token = "Bearer " + jwt.createAccessToken(owner);
        mvc.perform(get("/api/v1/card-notes/"+key)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/card-notes/"+key).header("Authorization", token).contentType("application/json").content("{\"text\":\"Ghi chú\",\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/card-notes/"+key).header("Authorization", token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/card-notes/"+key).header("Authorization", "Bearer "+jwt.createAccessToken(user())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.text").value(""));
        mvc.perform(put("/api/v1/card-notes/"+key).header("Authorization", token).contentType("application/json").content("{\"text\":\""+"x".repeat(5001)+"\",\"version\":1}"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/card-notes/invalid").header("Authorization", token)).andExpect(status().isBadRequest());
    }
    DeckDtos.DeckResponse deck(User owner) {
        return decks.create(owner.getId(), new DeckDtos.CreateDeckRequest("Deck", null, DomainTypes.SourceType.IMPORT, "import", "JSON", DomainTypes.Visibility.PRIVATE,
            Map.of(), List.of(new DeckDtos.CardRequest("水", "Nước", "みず", "Ghi chú gốc", DomainTypes.CardKind.KANJI, 0, null, Map.of(), List.of())), "IMPORT", null, null, List.of()));
    }
    @Test void sharingKeepsPersonalNotesPrivateUnlessOwnerOptsInAndCopiesAreIndependent() {
        var owner = user(); var recipient = user(); var optedInRecipient = user(); var source = deck(owner);
        String sourceKey = PersonalCardNoteService.personalKey(source.cards().getFirst().id());
        notes.put(owner.getId(), sourceKey, new PersonalCardNoteService.Update("Riêng tư", 0));
        String token = sharing.enable(owner.getId(), source.deck().id()).token();
        var copied = decks.get(recipient.getId(), sharing.copy(recipient.getId(), token).deckId());
        assertThat(notes.get(recipient.getId(), PersonalCardNoteService.personalKey(copied.cards().getFirst().id())).text()).isEmpty();
        sharing.enable(owner.getId(), source.deck().id(), new DeckShareService.ShareOptions(true));
        var received = decks.get(optedInRecipient.getId(), sharing.copy(optedInRecipient.getId(), token).deckId());
        String receivedKey = PersonalCardNoteService.personalKey(received.cards().getFirst().id());
        assertThat(notes.get(optedInRecipient.getId(), receivedKey).text()).isEqualTo("Riêng tư");
        assertThat(received.cards().getFirst().notes()).isEqualTo("Ghi chú gốc");
        notes.put(optedInRecipient.getId(), receivedKey, new PersonalCardNoteService.Update("Bản của người nhận", 1));
        assertThat(notes.get(owner.getId(), sourceKey).text()).isEqualTo("Riêng tư");
        sharing.revoke(owner.getId(), source.deck().id());
        assertThat(sharing.status(owner.getId(), source.deck().id()).includePersonalNotes()).isFalse();
    }
    @Test void groupShareConsentIsIndependentFromIndividualDeckConsent() {
        var owner = user(); var recipient = user(); var optedInRecipient = user(); var source = deck(owner);
        String sourceKey = PersonalCardNoteService.personalKey(source.cards().getFirst().id());
        notes.put(owner.getId(), sourceKey, new PersonalCardNoteService.Update("Group note", 0));
        groups.create(owner.getId(), new DeckGroupService.NameRequest("Group", null));
        UUID group = groups.list(owner.getId()).getFirst().id();
        groups.move(owner.getId(), source.deck().id(), new DeckGroupService.MoveRequest(group, null, source.deck().version()));
        sharing.enable(owner.getId(), source.deck().id(), new DeckShareService.ShareOptions(true));
        String token = groupSharing.enable(owner.getId(), group).token();
        UUID copiedDeck = groupSharing.copy(recipient.getId(), token).deckIds().getFirst();
        String copiedKey = PersonalCardNoteService.personalKey(decks.get(recipient.getId(), copiedDeck).cards().getFirst().id());
        assertThat(notes.get(recipient.getId(), copiedKey).text()).isEmpty();
        groupSharing.enable(owner.getId(), group, new DeckShareService.ShareOptions(true));
        UUID receivedDeck = groupSharing.copy(optedInRecipient.getId(), token).deckIds().getFirst();
        String receivedKey = PersonalCardNoteService.personalKey(decks.get(optedInRecipient.getId(), receivedDeck).cards().getFirst().id());
        assertThat(notes.get(optedInRecipient.getId(), receivedKey).text()).isEqualTo("Group note");
        groupSharing.revoke(owner.getId(), group);
        assertThat(groupSharing.status(owner.getId(), group).includePersonalNotes()).isFalse();
    }
}
