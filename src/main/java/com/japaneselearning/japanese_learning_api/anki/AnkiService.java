package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.common.ApiException;
import com.japaneselearning.japanese_learning_api.model.*;
import com.japaneselearning.japanese_learning_api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AnkiService {
    private final AnkiCardProgressRepository progressRepository;
    private final AnkiReviewLogRepository logs;
    private final CardRepository cards;
    private final DeckRepository decks;
    private final UserRepository users;
    private final AnkiScheduler scheduler;

    public AnkiService(AnkiCardProgressRepository progressRepository, AnkiReviewLogRepository logs,
                       CardRepository cards, DeckRepository decks, UserRepository users, AnkiScheduler scheduler) {
        this.progressRepository = progressRepository;
        this.logs = logs;
        this.cards = cards;
        this.decks = decks;
        this.users = users;
        this.scheduler = scheduler;
    }

    @Transactional(readOnly = true)
    public List<AnkiDtos.QueueCard> deckCards(UUID userId, UUID deckId) {
        decks.findByIdAndOwnerIdAndDeletedAtIsNull(deckId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bộ thẻ"));
        var progressByCard = progressRepository.findAllByUserIdAndCardDeckId(userId, deckId).stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getCard().getId(), p -> p));
        return cards.findAllByDeckIdAndDeletedAtIsNullOrderByPosition(deckId).stream()
                .map(card -> {
                    AnkiCardProgress progress = progressByCard.get(card.getId());
                    return queueCard(card, progress == null ? null : response(progress));
                }).toList();
    }

    @Transactional(readOnly = true)
    public List<AnkiDtos.QueueCard> queue(UUID userId, UUID deckId, int limit) {
        if (limit < 1 || limit > 200) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "limit phải từ 1 đến 200");
        List<AnkiDtos.QueueCard> result = new ArrayList<>();
        progressRepository.findDue(userId, deckId, Instant.now()).stream().limit(limit)
                .forEach(progress -> result.add(queueCard(progress.getCard(), response(progress))));
        if (result.size() < limit) {
            cards.findNewForAnki(userId, deckId).stream().limit(limit - result.size())
                    .forEach(card -> result.add(queueCard(card, null)));
        }
        return result;
    }

    @Transactional
    public AnkiDtos.ReviewResponse review(UUID userId, UUID cardId, AnkiDtos.ReviewRequest request) {
        Card card = cards.findByIdAndDeckOwnerIdAndDeletedAtIsNull(cardId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thẻ"));
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        AnkiCardProgress progress = progressRepository.findByUserIdAndCardId(userId, cardId)
                .orElseGet(() -> newProgress(user, card));
        DomainTypes.AnkiState previousState = progress.getState();
        Integer previousInterval = progress.getIntervalDays();
        Instant reviewedAt = Instant.now();
        scheduler.apply(progress, request.rating(), reviewedAt);
        progressRepository.save(progress);

        AnkiReviewLog log = new AnkiReviewLog();
        log.setId(UUID.randomUUID());
        log.setUser(user);
        log.setCard(card);
        log.setRating(request.rating());
        log.setPreviousState(previousState);
        log.setNewState(progress.getState());
        log.setPreviousIntervalDays(previousInterval);
        log.setNewIntervalDays(progress.getIntervalDays());
        log.setResponseTimeMs(request.responseTimeMs());
        log.setReviewedAt(reviewedAt);
        logs.save(log);
        return new AnkiDtos.ReviewResponse(cardId, request.rating(), response(progress));
    }

    private AnkiCardProgress newProgress(User user, Card card) {
        AnkiCardProgress progress = new AnkiCardProgress();
        progress.setId(UUID.randomUUID());
        progress.setUser(user);
        progress.setCard(card);
        progress.setState(DomainTypes.AnkiState.NEW);
        progress.setEaseFactor(new BigDecimal("2.50"));
        progress.setDueAt(Instant.now());
        return progress;
    }

    private AnkiDtos.QueueCard queueCard(Card card, AnkiDtos.ProgressResponse progress) {
        return new AnkiDtos.QueueCard(card.getId(), card.getDeck().getId(), card.getDeck().getName(), card.getFront(),
                card.getBack(), card.getReading(), card.getNotes(), card.getKind(), progress);
    }

    private AnkiDtos.ProgressResponse response(AnkiCardProgress progress) {
        return new AnkiDtos.ProgressResponse(progress.getState(), progress.getEaseFactor(), progress.getIntervalMinutes(),
                progress.getIntervalDays(), progress.getDueAt(), progress.getRepetitions(), progress.getLapses(),
                progress.getLastReviewedAt());
    }
}
