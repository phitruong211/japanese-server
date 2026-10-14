package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.CardRepository;
import com.japaneselearning.japanese_learning_api.notes.*;
import jakarta.persistence.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.*;

/** Internal copy operations. Callers hold the source and recipient user locks for the entire transaction. */
@Component
public class SharedDeckContent {
    private final CardRepository cards;
    private final DeckService deckService;
    private final PersonalCardNoteRepository personalNotes;
    @PersistenceContext private EntityManager em;
    public SharedDeckContent(CardRepository cards, DeckService deckService, PersonalCardNoteRepository personalNotes) {
        this.cards = cards; this.deckService = deckService; this.personalNotes = personalNotes;
    }
    public void lockOwners(UUID... ids) {
        Arrays.stream(ids).distinct().sorted().forEach(id -> {
            if (em.find(User.class, id, LockModeType.PESSIMISTIC_WRITE) == null)
                throw ApiException.notFound("Không tìm thấy người dùng");
        });
    }
    public void requireCardLimit(List<UUID> deckIds) {
        if (!deckIds.isEmpty() && cards.findShareCardIds(deckIds, PageRequest.of(0, 20_001)).size() > 20_000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ có thể nhận tối đa 20.000 thẻ trong một lần");
    }
    public UUID copy(UUID userId, Deck source, String key) {
        return copy(userId, source, key, source.isSharePersonalNotes());
    }
    public UUID copy(UUID userId, Deck source, String key, boolean includePersonalNotes) {
        var sourceCards = cards.findShareCardsForCopy(source.getId());
        var includedNotes = new HashMap<String, PersonalCardNote>();
        if (includePersonalNotes) {
            var noteKeys = sourceCards.stream().map(c -> PersonalCardNoteService.personalKey(c.getId())).toList();
            for (int offset = 0; offset < noteKeys.size(); offset += 500)
                personalNotes.findByOwnerIdAndNoteKeyIn(source.getOwner().getId(), noteKeys.subList(offset, Math.min(offset + 500, noteKeys.size())))
                        .forEach(note -> includedNotes.put(note.getNoteKey(), note));
        }
        var requests = sourceCards.stream().map(c -> new DeckDtos.CardRequest(
                c.getFront(), c.getBack(), c.getReading(), c.getNotes(), c.getKind(), c.getPosition(), null,
                sharedExtraData(c), null)).toList();
        var result = deckService.create(userId, new DeckDtos.CreateDeckRequest(
                source.getName(), source.getDescription(), DomainTypes.SourceType.IMPORT,
                "Bộ thẻ được chia sẻ", "SHARED", DomainTypes.Visibility.PRIVATE,
                new HashMap<>(source.getTemplateConfig()), requests, "IMPORT", null, null, source.getTags()));
        em.find(Deck.class, result.deck().id()).setImportKey(key);
        for (int i = 0; i < sourceCards.size(); i++) {
            Card original = sourceCards.get(i);
            Card copied = em.find(Card.class, result.cards().get(i).id());
            copied.setDictionaryIdentity(original.getDictionaryIdentity());
            copied.setDictionaryProvenance(original.getDictionaryProvenance());
            var personal = includedNotes.get(PersonalCardNoteService.personalKey(original.getId()));
            if (personal != null && !personal.getContent().isBlank()) {
                var received = new PersonalCardNote(); received.setId(UUID.randomUUID()); received.setOwnerId(userId);
                received.setNoteKey(PersonalCardNoteService.personalKey(copied.getId())); received.setContent(personal.getContent());
                received.setRevision(1); received.setUpdatedAt(java.time.Instant.now()); personalNotes.save(received);
            }
            if (original.getDictionaryProvenance() != null) copied.getExtraData().put("dictionaryRef", original.getDictionaryProvenance());
        }
        return result.deck().id();
    }
    private Map<String, Object> sharedExtraData(Card card) {
        Map<String, Object> data = new HashMap<>(card.getExtraData());
        if (card.getDictionaryProvenance() != null) data.put("dictionaryRef", card.getDictionaryProvenance());
        data.remove("sourceRef");
        data.remove("sourceSheet");
        return data;
    }
}
