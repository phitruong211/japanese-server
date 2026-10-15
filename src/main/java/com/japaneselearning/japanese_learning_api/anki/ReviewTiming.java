package com.japaneselearning.japanese_learning_api.anki;

import java.time.Duration;

/** Telemetry validation only: never changes the learner's rating or FSRS schedule. */
public final class ReviewTiming {
    private ReviewTiming() {}

    public static boolean isValid(AnkiDtos.ReviewRequest request) {
        if (!Boolean.TRUE.equals(request.isTimeValid()) || request.recallTimeMs() == null
                || request.decisionTimeMs() == null || request.recallTimeMs() <= 0
                || request.decisionTimeMs() <= 0 || request.shownAt() == null
                || request.revealedAt() == null || request.ratedAt() == null) return false;
        // Wall-clock timestamps are diagnostic only. Active durations come from a monotonic clock.
        // A clock adjustment or impossible duration invalidates telemetry, not the review itself.
        long recallWall = Duration.between(request.shownAt(), request.revealedAt()).toMillis();
        long decisionWall = Duration.between(request.revealedAt(), request.ratedAt()).toMillis();
        return recallWall >= request.recallTimeMs() && decisionWall >= request.decisionTimeMs();
    }
}
