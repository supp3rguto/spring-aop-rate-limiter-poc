package com.backend.ratelimiter.controller.dto;

import java.time.Instant;

public record ResourceResponse(
        String id,
        String name,
        Instant createdAt
) {
}