package com.backend.ratelimiter.service;

import com.backend.ratelimiter.exception.ApiKeyInvalidException;
import com.backend.ratelimiter.model.ApiKey;
import com.backend.ratelimiter.model.EndpointCost;
import com.backend.ratelimiter.model.Plan;
import com.backend.ratelimiter.repository.ApiKeyRepository;
import com.backend.ratelimiter.repository.EndpointCostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitingService {

    private final ApiKeyRepository apiKeyRepository;
    private final EndpointCostRepository endpointCostRepository;

    public RateLimitingService(
            ApiKeyRepository apiKeyRepository,
            EndpointCostRepository endpointCostRepository
    ) {
        this.apiKeyRepository = apiKeyRepository;
        this.endpointCostRepository = endpointCostRepository;
    }

    @Transactional
    public boolean tryConsumeToken(String apiKeyId, String endpointName) {
        long now = System.currentTimeMillis();

        int cost = endpointCostRepository.findByEndpointName(endpointName)
                .map(EndpointCost::getTokenCost)
                .orElse(1);

        ApiKey key = apiKeyRepository.findById(apiKeyId)
                .orElseThrow(() -> new ApiKeyInvalidException("Invalid API key."));

        Plan plan = key.getPlan();
        int capacity = plan.getBucketCapacity();
        long refillPeriodMs = plan.getRefillRateSeconds() * 1000L;

        long timeElapsed = now - key.getLastRefillTimestamp();
        if (timeElapsed >= refillPeriodMs) {
            key.setTokens(capacity);
            key.setLastRefillTimestamp(now);
        }

        if (key.getTokens() >= cost) {
            key.setTokens(key.getTokens() - cost);
            apiKeyRepository.save(key);
            return true;
        } else {
            apiKeyRepository.save(key);
            return false;
        }
    }
}