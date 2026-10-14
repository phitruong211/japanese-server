package com.japaneselearning.japanese_learning_api.deck;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.CardRepository;
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
    @PersistenceContext private EntityManager em;
    public SharedDeckContent(CardRepository cards, DeckService deckService) {
        this.cards = cards; this.deckService = deckService;
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
        var sourceCards = cards.findShareCardsForCopy(source.getId());
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
