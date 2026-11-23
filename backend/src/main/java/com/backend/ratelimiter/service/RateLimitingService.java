package com.backend.ratelimiter.service;

import com.backend.ratelimiter.exception.ApiKeyInvalidException;
import com.backend.ratelimiter.model.ApiKey;
import com.backend.ratelimiter.model.EndpointCost;
import com.backend.ratelimiter.model.Plan;
import com.backend.ratelimiter.repository.ApiKeyRepository;
import com.backend.ratelimiter.repository.EndpointCostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RateLimitingService {

    private static final int DEFAULT_ENDPOINT_COST = 1;

    private final ApiKeyRepository apiKeyRepository;
    private final EndpointCostRepository endpointCostRepository;
    private final Clock clock;

    public RateLimitingService(
            ApiKeyRepository apiKeyRepository,
            EndpointCostRepository endpointCostRepository,
            Clock clock
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.endpointCostRepository = endpointCostRepository;
        this.clock = clock;
    }

    @Transactional
    public RateLimitResult tryConsume(String apiKeyId, String endpointName) {
        long now = clock.millis();

        int cost = endpointCostRepository.findByEndpointName(endpointName)
                .map(EndpointCost::getTokenCost)
                .orElse(DEFAULT_ENDPOINT_COST);

        ApiKey key = apiKeyRepository.findByIdForUpdate(apiKeyId)
                .orElseThrow(() -> new ApiKeyInvalidException("Invalid API key."));

        Plan plan = key.getPlan();
        int capacity = plan.getBucketCapacity();
        long refillPeriodMs = plan.getRefillRateSeconds() * 1000L;

        int availableTokens = key.getTokens();
        long lastRefill = key.getLastRefillTimestamp();

        if (lastRefill <= 0 || availableTokens >= capacity) {
            availableTokens = Math.min(availableTokens, capacity);
            if (lastRefill <= 0) {
                availableTokens = capacity;
            }
            lastRefill = now;
        } else {
            long elapsedMs = Math.max(0L, now - lastRefill);
            if (elapsedMs >= refillPeriodMs) {
                availableTokens = capacity;
                lastRefill = now;
            } else {
                long tokensToAdd = (elapsedMs * capacity) / refillPeriodMs;
                if (tokensToAdd > 0) {
                    availableTokens = (int) Math.min(capacity, availableTokens + tokensToAdd);
                    if (availableTokens == capacity) {
                        lastRefill = now;
                    } else {
                        long consumedMs = (tokensToAdd * refillPeriodMs) / capacity;
                        lastRefill = lastRefill + consumedMs;
                    }
                }
            }
        }

        if (availableTokens >= cost) {
            int remaining = availableTokens - cost;
            key.setTokens(remaining);
            key.setLastRefillTimestamp(lastRefill);

            long resetAtEpochSeconds = calculateResetEpochSeconds(
                    remaining, capacity, refillPeriodMs, lastRefill, now
            );
            return new RateLimitResult(true, capacity, remaining, resetAtEpochSeconds, 0L);
        }

        // Rejected requests do not mutate the entity, avoiding unnecessary DB writes under burst load.
        long retryAfterSeconds = cost > capacity
                ? plan.getRefillRateSeconds()
                : calculateRetryAfterSeconds(cost - availableTokens, capacity, refillPeriodMs, lastRefill, now);

        long resetAtEpochSeconds = calculateResetEpochSeconds(
                availableTokens, capacity, refillPeriodMs, lastRefill, now
        );
        return new RateLimitResult(false, capacity, availableTokens, resetAtEpochSeconds, retryAfterSeconds);
    }

    private long calculateRetryAfterSeconds(
            int tokensNeeded,
            int capacity,
            long refillPeriodMs,
            long lastRefill,
            long now
    ) {
        long msUntilEnough = ceilDiv((long) tokensNeeded * refillPeriodMs, capacity);
        long elapsedSinceRefill = Math.max(0L, now - lastRefill);
        long remainingMs = Math.max(1L, msUntilEnough - elapsedSinceRefill);
        return ceilDiv(remainingMs, 1000L);
    }

    private long calculateResetEpochSeconds(
            int remainingTokens,
            int capacity,
            long refillPeriodMs,
            long lastRefill,
            long now
    ) {
        if (remainingTokens >= capacity) {
            return now / 1000L;
        }
        int missingTokens = capacity - remainingTokens;
        long msToFull = ceilDiv((long) missingTokens * refillPeriodMs, capacity);
        long resetAtMs = Math.max(now, lastRefill + msToFull);
        return ceilDiv(resetAtMs, 1000L);
    }

    private long ceilDiv(long numerator, long denominator) {
        return (numerator + denominator - 1) / denominator;
    }
}