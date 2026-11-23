package com.backend.ratelimiter.aop;

import com.backend.ratelimiter.exception.ApiKeyInvalidException;
import com.backend.ratelimiter.exception.RateLimitException;
import com.backend.ratelimiter.service.RateLimitResult;
import com.backend.ratelimiter.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class RateLimiterAspect {

    private final RateLimitingService rateLimitingService;

    public RateLimiterAspect(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Before("@annotation(rateLimited)")
    public void beforeRateLimitedMethod(RateLimited rateLimited) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String apiKey = request.getHeader("X-API-KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiKeyInvalidException("Missing X-API-KEY header.");
        }

        RateLimitResult result = rateLimitingService.tryConsume(apiKey, rateLimited.endpointName());
        if (!result.allowed()) {
            throw new RateLimitException("Rate limit exceeded.");
        }
    }
}