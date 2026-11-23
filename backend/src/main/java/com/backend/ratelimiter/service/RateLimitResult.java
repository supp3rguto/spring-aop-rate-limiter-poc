package com.backend.ratelimiter.service;

public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long resetAtEpochSeconds,
        long retryAfterSeconds
) {
}