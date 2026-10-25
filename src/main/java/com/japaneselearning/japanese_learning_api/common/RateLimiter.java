package com.japaneselearning.japanese_learning_api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window counter per key, in memory. It protects endpoints that anyone can call (share-code lookups, library
 * browsing) from guessing and scraping. State is per server instance; with several instances the effective limit is
 * multiplied, which is acceptable for abuse control but not for billing-grade quotas.
 */
@Component
public class RateLimiter {
    private record Window(long start, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean trustForwarded;

    /** Behind a reverse proxy every request arrives from the proxy; set app.rate-limit.trust-forwarded only if it overwrites X-Forwarded-For. */
    public RateLimiter(@Value("${app.rate-limit.trust-forwarded:false}") boolean trustForwarded) { this.trustForwarded = trustForwarded; }

    public void check(String bucket, String client, int max, Duration window, String message) {
        long now = System.currentTimeMillis();
        if (windows.size() > 20_000) windows.entrySet().removeIf(entry -> now - entry.getValue().start() > 3_600_000);
        var current = windows.compute(bucket + ":" + client, (key, old) ->
                old == null || now - old.start() >= window.toMillis() ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (current.count() > max) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public String client(HttpServletRequest request) {
        String forwarded = trustForwarded ? request.getHeader("X-Forwarded-For") : null;
        return forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
