package com.japaneselearning.japanese_learning_api.anki;

import com.japaneselearning.japanese_learning_api.model.AnkiCardProgress;
import com.japaneselearning.japanese_learning_api.model.DomainTypes;
import com.japaneselearning.japanese_learning_api.model.UserSettings;
import io.github.openspacedrepetition.Card;
import io.github.openspacedrepetition.Rating;
import io.github.openspacedrepetition.Scheduler;
import io.github.openspacedrepetition.State;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Isolates the official FSRS library from the application's persistence model. */
@Component
public class AnkiScheduler {
    public void apply(AnkiCardProgress progress, DomainTypes.AnkiRating rating, Instant now,
                      Integer responseTimeMs, UserSettings settings, boolean enableFuzzing) {
        DomainTypes.AnkiState previousState = progress.getState();
        Instant previousReview = progress.getLastReviewedAt();
        Card input = toFsrsCard(progress, now);
        Scheduler scheduler = Scheduler.builder()
                .desiredRetention(settings.getSrsDesiredRetention().doubleValue())
                .learningSteps(new Duration[]{
                        Duration.ofMinutes(settings.getSrsAgainMinutes()),
                        Duration.ofMinutes(settings.getSrsGoodMinutes())})
                .relearningSteps(new Duration[]{Duration.ofMinutes(settings.getSrsAgainMinutes())})
                .maximumInterval(36500)
                .enableFuzzing(enableFuzzing)
                .build();
        Card result = scheduler.reviewCard(input, Rating.valueOf(rating.name()), now, responseTimeMs).card();

        progress.setState(toDomainState(result.getState()));
        progress.setFsrsStep(result.getStep());
        progress.setFsrsStability(result.getStability());
        progress.setFsrsDifficulty(result.getDifficulty());
        progress.setFsrsAlgorithm("fsrs-6");
        progress.setDueAt(result.getDue());
        progress.setFirstReviewedAt(progress.getFirstReviewedAt() == null ? now : progress.getFirstReviewedAt());
        progress.setLastReviewedAt(result.getLastReview() == null ? now : result.getLastReview());
        progress.setLastRating(rating);
        progress.setRepetitions(progress.getRepetitions() + 1);
        if (rating == DomainTypes.AnkiRating.AGAIN && previousState == DomainTypes.AnkiState.REVIEW)
            progress.setLapses(progress.getLapses() + 1);

        int elapsedDays = previousReview == null ? 0 : Math.max(0, (int) ChronoUnit.DAYS.between(previousReview, now));
        int scheduledDays = Math.max(0, (int) ChronoUnit.DAYS.between(now, result.getDue()));
        progress.setFsrsElapsedDays(elapsedDays);
        progress.setFsrsScheduledDays(scheduledDays);
        Duration interval = Duration.between(now, result.getDue());
        if (interval.compareTo(Duration.ofDays(1)) < 0) {
            progress.setIntervalMinutes(Math.max(1, (int) Math.ceil(interval.toSeconds() / 60.0)));
            progress.setIntervalDays(null);
        } else {
            progress.setIntervalMinutes(null);
            progress.setIntervalDays(Math.max(1, scheduledDays));
        }
        progress.setEaseFactor(BigDecimal.valueOf(Math.max(1.3,
                3.0 - (result.getDifficulty() == null ? 5.0 : result.getDifficulty()) * 0.1)));
    }

    private Card toFsrsCard(AnkiCardProgress progress, Instant now) {
        int cardId = progress.getCard() == null ? 1 : progress.getCard().getId().hashCode();
        Card.Builder builder = Card.builder().cardId(cardId).due(progress.getDueAt() == null ? now : progress.getDueAt());
        if (progress.getState() == DomainTypes.AnkiState.NEW) return builder.build();

        State state = switch (progress.getState()) {
            case REVIEW -> State.REVIEW;
            case RELEARNING -> State.RELEARNING;
            default -> State.LEARNING;
        };
        builder.state(state).lastReview(progress.getLastReviewedAt());
        if (state != State.REVIEW) builder.step(progress.getFsrsStep() == null ? 0 : progress.getFsrsStep());
        else if (progress.getFsrsStep() != null) builder.step(progress.getFsrsStep());
        if (progress.getFsrsStability() != null) builder.stability(progress.getFsrsStability());
        else if (state == State.REVIEW) builder.stability(Math.max(0.1, progress.getIntervalDays() == null ? 1 : progress.getIntervalDays()));
        if (progress.getFsrsDifficulty() != null) builder.difficulty(progress.getFsrsDifficulty());
        else if (state == State.REVIEW) builder.difficulty(5.0);
        return builder.build();
    }

    private DomainTypes.AnkiState toDomainState(State state) {
        return switch (state) {
            case LEARNING -> DomainTypes.AnkiState.LEARNING;
            case REVIEW -> DomainTypes.AnkiState.REVIEW;
            case RELEARNING -> DomainTypes.AnkiState.RELEARNING;
        };
    }
}
