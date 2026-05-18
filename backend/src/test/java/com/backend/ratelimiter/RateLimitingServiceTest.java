package com.backend.ratelimiter.service;

import com.backend.ratelimiter.exception.ApiKeyInvalidException;
import com.backend.ratelimiter.exception.RateLimitException;
import com.backend.ratelimiter.model.ApiKey;
import com.backend.ratelimiter.model.ApiLimit;
import com.backend.ratelimiter.repository.ApiKeyRepository;
import com.backend.ratelimiter.repository.ApiLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private ApiLimitRepository apiLimitRepository;

    @InjectMocks
    private RateLimitingService rateLimitingService;

    private ApiKey validApiKey;
    private ApiLimit userLimit;

    @BeforeEach
    void setUp() {
        validApiKey = new ApiKey();
        validApiKey.setKey("test-token-123");
        
        userLimit = new ApiLimit();
        userLimit.setRemainingQuota(100); // Tem limite de sobra
    }

    @Test
    @DisplayName("Deve lançar ApiKeyInvalidException se a chave não existir")
    void shouldThrowExceptionWhenApiKeyIsInvalid() {
        when(apiKeyRepository.findByKey("chave-falsa")).thenReturn(Optional.empty());

        assertThrows(ApiKeyInvalidException.class, () -> {
            rateLimitingService.checkLimit("chave-falsa", 10);
        });
    }

    @Test
    @DisplayName("Deve lançar RateLimitException quando a cota acabar (HTTP 429)")
    void shouldThrowRateLimitExceptionWhenQuotaExceeded() {
        userLimit.setRemainingQuota(5); // Cota menor que o custo

        when(apiKeyRepository.findByKey("test-token-123")).thenReturn(Optional.of(validApiKey));
        when(apiLimitRepository.findByApiKey(validApiKey)).thenReturn(Optional.of(userLimit));

        RateLimitException exception = assertThrows(RateLimitException.class, () -> {
            rateLimitingService.checkLimit("test-token-123", 10); // Tenta gastar 10 tendo só 5
        });

        assertTrue(exception.getMessage().contains("Rate limit exceeded"));
    }
}