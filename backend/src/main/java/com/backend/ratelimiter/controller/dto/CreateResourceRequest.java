package com.backend.ratelimiter.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(
        @NotBlank(message = "Resource name must not be blank.")
        @Size(max = 100, message = "Resource name must be at most 100 characters.")
        String name
) {
}