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
import tools.jackson.databind.ObjectMapper;

@Service
public class AnkiService {
    private final AnkiCardProgressRepository progressRepository;
    private final AnkiReviewLogRepository logs;
    private final CardRepository cards;
    private final DeckRepository decks;
    private final UserRepository users;
    private final AnkiScheduler scheduler;
    private final UserSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper;

    public AnkiService(AnkiCardProgressRepository progressRepository, AnkiReviewLogRepository logs,
                       CardRepository cards, DeckRepository decks, UserRepository users, AnkiScheduler scheduler,
                       UserSettingsRepository settingsRepository, ObjectMapper objectMapper) {
        this.progressRepository = progressRepository;
        this.logs = logs;
        this.cards = cards;
        this.decks = decks;
        this.users = users;
        this.scheduler = scheduler;
        this.settingsRepository = settingsRepository;
        this.objectMapper = objectMapper;
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
        if (deckId != null) decks.findByIdAndOwnerIdAndDeletedAtIsNull(deckId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bộ thẻ"));
        List<AnkiDtos.QueueCard> result = new ArrayList<>();
        progressRepository.findDue(userId, deckId, Instant.now(), org.springframework.data.domain.PageRequest.of(0, limit)).stream()
                .forEach(progress -> result.add(queueCard(progress.getCard(), response(progress))));
        if (result.size() < limit) {
            User owner = users.findById(userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
            java.time.ZoneId zone;
            try { zone = java.time.ZoneId.of(owner.getTimezone()); }
            catch (RuntimeException ignored) { zone = java.time.ZoneOffset.UTC; }
            Instant dayStart = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant();
            int dailyNewLimit = settingsRepository.findById(userId).map(settings -> settings.getSrsDailyNewLimit()).orElse(20);
            long startedToday = progressRepository.countNewStartedToday(userId, deckId, dayStart);
            int allowance = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) dailyNewLimit - startedToday));
            int newLimit = Math.min(limit - result.size(), allowance);
            if (newLimit > 0) cards.findNewForAnki(userId, deckId, org.springframework.data.domain.PageRequest.of(0, newLimit)).stream()
                    .forEach(card -> result.add(queueCard(card, null)));
        }
        return result;
    }

    @Transactional
    public AnkiDtos.ReviewResponse review(UUID userId, UUID cardId, AnkiDtos.ReviewRequest request) {
        UUID clientReviewId = request.clientReviewId() == null ? UUID.randomUUID() : request.clientReviewId();
        // Serialize reviews for the same card before checking the idempotency key. This closes the
        // race where two browser retries could otherwise calculate and persist the same review twice.
        Card card = cards.findReviewCardForUpdate(cardId, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thẻ"));
        var existing = logs.findByUserIdAndClientReviewId(userId, clientReviewId);
        if (existing.isPresent()) {
            if (existing.get().getUndoneAt() != null) throw ApiException.conflict("Lượt ôn đã được hoàn tác");
            if (!existing.get().getCard().getId().equals(cardId) || existing.get().getRating() != request.rating())
                throw ApiException.conflict("Mã lượt ôn đã được dùng cho thẻ hoặc đánh giá khác");
            var saved = objectMapper.readValue(existing.get().getResultProgress(), AnkiDtos.ProgressResponse.class);
            return new AnkiDtos.ReviewResponse(existing.get().getCard().getId(), existing.get().getRating(), saved);
        }
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        var previousProgress = progressRepository.findByUserIdAndCardId(userId, cardId);
        AnkiCardProgress progress = previousProgress.orElseGet(() -> newProgress(user, card));
        String beforeProgress = objectMapper.writeValueAsString(response(progress));
        if (request.expectedRepetitions() != null && request.expectedRepetitions() != progress.getRepetitions())
            throw ApiException.conflict("Thẻ đã được ôn trên phiên khác. Hãy mở lại phiên học để tải lịch mới.");
        DomainTypes.AnkiState previousState = progress.getState();
        Integer previousInterval = progress.getIntervalDays();
        Instant reviewedAt = ReviewSchedulingTime.resolve(request.ratedAt(), progress.getLastReviewedAt(), Instant.now());
        Double predictedRecall = scheduler.predictedRecall(progress, reviewedAt);
        Double elapsedDays = progress.getLastReviewedAt() == null ? null
                : Math.max(0, java.time.Duration.between(progress.getLastReviewedAt(), reviewedAt).toMillis() / 86400000.0);
        UserSettings settings = settingsRepository.findById(userId).orElseGet(UserSettings::new);
        scheduler.apply(progress, request.rating(), reviewedAt, null, settings, true);
        progress.setDueAt(precise(progress.getDueAt()));
        progress.setLastReviewedAt(precise(progress.getLastReviewedAt()));
        progress.setFirstReviewedAt(precise(progress.getFirstReviewedAt()));
        progressRepository.save(progress);

        AnkiReviewLog log = new AnkiReviewLog();
        log.setId(UUID.randomUUID());
        log.setBeforeProgress(beforeProgress);
        log.setProgressExisted(previousProgress.isPresent());
        log.setUser(user);
        log.setCard(card);
        log.setRating(request.rating());
        log.setPreviousState(previousState);
        log.setNewState(progress.getState());
        log.setPreviousIntervalDays(previousInterval);
        log.setNewIntervalDays(progress.getIntervalDays());
        log.setResponseTimeMs(request.responseTimeMs());
        log.setRecallTimeMs(request.recallTimeMs());
        log.setDecisionTimeMs(request.decisionTimeMs());
        log.setTimeValid(ReviewTiming.isValid(request));
        log.setShownAt(request.shownAt());
        log.setRevealedAt(request.revealedAt());
        log.setRatedAt(request.ratedAt());
        log.setElapsedDays(elapsedDays);
        log.setPredictedRecall(predictedRecall);
        log.setSchedulerVersion(AnkiScheduler.VERSION);
        log.setReviewedAt(reviewedAt);
        log.setClientReviewId(clientReviewId);
        AnkiDtos.ProgressResponse result = response(progress);
        log.setResultProgress(objectMapper.writeValueAsString(result));
        logs.save(log);
        return new AnkiDtos.ReviewResponse(cardId, request.rating(), result);
    }

    @Transactional
    public AnkiDtos.UndoResponse undo(UUID userId, UUID cardId, UUID clientReviewId) {
        cards.findReviewCardForUpdate(cardId, userId).orElseThrow(() -> ApiException.notFound("Không tìm thấy thẻ"));
        var log = logs.findByUserIdAndClientReviewId(userId, clientReviewId)
                .filter(l -> l.getCard().getId().equals(cardId)).orElseThrow(() -> ApiException.notFound("Không tìm thấy lượt ôn"));
        if (log.getBeforeProgress() == null) throw ApiException.conflict("Lượt ôn cũ không hỗ trợ hoàn tác");
        var current = progressRepository.findByUserIdAndCardId(userId, cardId);
        var before = objectMapper.readValue(log.getBeforeProgress(), AnkiDtos.ProgressResponse.class);
        if (log.getUndoneAt() != null) return new AnkiDtos.UndoResponse(cardId, current.map(this::response).orElse(before));
        if (current.isEmpty() || !response(current.get()).equals(objectMapper.readValue(log.getResultProgress(), AnkiDtos.ProgressResponse.class)))
            throw ApiException.conflict("Thẻ đã thay đổi trên phiên khác. Hãy tải lại lịch ôn.");
        if (Boolean.FALSE.equals(log.getProgressExisted())) progressRepository.delete(current.get());
        else restore(current.get(), before);
        log.setUndoneAt(Instant.now());
        return new AnkiDtos.UndoResponse(cardId, before);
    }

    private void restore(AnkiCardProgress p, AnkiDtos.ProgressResponse s) {
        p.setState(s.state()); p.setEaseFactor(s.easeFactor()); p.setIntervalMinutes(s.intervalMinutes());
        p.setIntervalDays(s.intervalDays()); p.setDueAt(s.dueAt()); p.setRepetitions(s.repetitions());
        p.setLapses(s.lapses()); p.setLastReviewedAt(s.lastReviewedAt()); p.setFirstReviewedAt(s.firstReviewedAt());
        p.setFsrsAlgorithm(s.algorithm()); p.setFsrsStability(s.stability()); p.setFsrsDifficulty(s.difficulty());
        p.setFsrsElapsedDays(s.elapsedDays()); p.setFsrsScheduledDays(s.scheduledDays());
        p.setFsrsStep(s.learningSteps()); p.setLastRating(s.lastRating());
    }

    private Instant precise(Instant value) { return value == null ? null : value.truncatedTo(java.time.temporal.ChronoUnit.MICROS); }

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
                card.getBack(), card.getReading(), card.getNotes(), card.getKind(), card.getExtraData(), progress, card.getNoteType(), card.getFields());
    }

    private AnkiDtos.ProgressResponse response(AnkiCardProgress progress) {
        return new AnkiDtos.ProgressResponse(progress.getState(), progress.getEaseFactor().setScale(2, java.math.RoundingMode.HALF_UP), progress.getIntervalMinutes(),
                progress.getIntervalDays(), precise(progress.getDueAt()), progress.getRepetitions(), progress.getLapses(),
                precise(progress.getLastReviewedAt()), progress.getFsrsAlgorithm(), progress.getFsrsStability(),
                progress.getFsrsDifficulty(), progress.getFsrsElapsedDays(), progress.getFsrsScheduledDays(),
                progress.getFsrsStep(), precise(progress.getFirstReviewedAt()), progress.getLastRating());
    }
}
