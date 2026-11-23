package com.backend.ratelimiter.exception;

import com.backend.ratelimiter.service.RateLimitResult;

public class RateLimitException extends RuntimeException {

    private final RateLimitResult result;

    public RateLimitException(String message, RateLimitResult result) {
        super(message);
        this.result = result;
    }

    public RateLimitResult getResult() {
        return result;
    }
}