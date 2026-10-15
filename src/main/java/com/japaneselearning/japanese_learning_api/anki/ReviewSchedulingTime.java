package com.japaneselearning.japanese_learning_api.anki;

import java.time.Instant;
import java.time.Duration;

/** Delayed delivery must not turn a one-minute retry into one minute after reconnect. */
public final class ReviewSchedulingTime {
    private ReviewSchedulingTime() {}
    public static Instant resolve(Instant ratedAt, Instant previousReview, Instant receivedAt) {
        if (ratedAt == null || ratedAt.isAfter(receivedAt.plusSeconds(30))
                || ratedAt.isBefore(receivedAt.minus(Duration.ofDays(30)))
                || (previousReview != null && ratedAt.isBefore(previousReview))) return receivedAt;
        return ratedAt.isAfter(receivedAt) ? receivedAt : ratedAt;
    }
}
