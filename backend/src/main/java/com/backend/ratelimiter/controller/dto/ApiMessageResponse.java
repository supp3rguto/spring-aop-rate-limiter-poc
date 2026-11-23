package com.backend.ratelimiter.controller.dto;

import java.time.Instant;

public record ApiMessageResponse(
        String message,
        Instant timestamp
) {
}