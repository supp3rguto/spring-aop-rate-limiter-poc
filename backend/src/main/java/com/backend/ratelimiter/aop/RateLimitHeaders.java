package com.backend.ratelimiter.aop;

import com.backend.ratelimiter.service.RateLimitResult;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

public final class RateLimitHeaders {

    public static final String API_KEY = "X-API-KEY";
    public static final String LIMIT = "X-RateLimit-Limit";
    public static final String REMAINING = "X-RateLimit-Remaining";
    public static final String RESET = "X-RateLimit-Reset";
    public static final String RETRY_AFTER = HttpHeaders.RETRY_AFTER;

    private RateLimitHeaders() {
    }

    public static void applyTo(HttpServletResponse response, RateLimitResult result) {
        response.setHeader(LIMIT, String.valueOf(result.limit()));
        response.setHeader(REMAINING, String.valueOf(result.remaining()));
        response.setHeader(RESET, String.valueOf(result.resetAtEpochSeconds()));
    }

    public static HttpHeaders toHttpHeaders(RateLimitResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LIMIT, String.valueOf(result.limit()));
        headers.set(REMAINING, String.valueOf(result.remaining()));
        headers.set(RESET, String.valueOf(result.resetAtEpochSeconds()));
        if (!result.allowed()) {
            headers.set(RETRY_AFTER, String.valueOf(result.retryAfterSeconds()));
        }
        return headers;
    }
}