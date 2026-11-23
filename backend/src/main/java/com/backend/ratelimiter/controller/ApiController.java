package com.backend.ratelimiter.controller;

import com.backend.ratelimiter.aop.RateLimited;
import com.backend.ratelimiter.controller.dto.ApiMessageResponse;
import com.backend.ratelimiter.controller.dto.CreateResourceRequest;
import com.backend.ratelimiter.controller.dto.ResourceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final Clock clock;

    public ApiController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/public")
    public ResponseEntity<ApiMessageResponse> publicEndpoint() {
        return ResponseEntity.ok(new ApiMessageResponse("Public endpoint accessed.", clock.instant()));
    }

    @GetMapping("/limited")
    @RateLimited(endpointName = "get_limited_data")
    public ResponseEntity<ApiMessageResponse> limitedEndpoint() {
        return ResponseEntity.ok(new ApiMessageResponse("Limited endpoint accessed.", clock.instant()));
    }

    @PostMapping("/resources")
    @RateLimited(endpointName = "create_resource")
    public ResponseEntity<ResourceResponse> createResource(@Valid @RequestBody CreateResourceRequest request) {
        ResourceResponse resource = new ResourceResponse(
                UUID.randomUUID().toString(),
                request.name().trim(),
                clock.instant()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(resource);
    }
}