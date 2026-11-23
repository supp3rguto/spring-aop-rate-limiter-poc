package com.backend.ratelimiter.aop;

import com.backend.ratelimiter.exception.ApiKeyInvalidException;
import com.backend.ratelimiter.exception.RateLimitException;
import com.backend.ratelimiter.service.RateLimitResult;
import com.backend.ratelimiter.service.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
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

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException("@RateLimited can only be applied within an active HTTP request.");
        }

        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();

        String apiKey = request.getHeader(RateLimitHeaders.API_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiKeyInvalidException("Missing X-API-KEY header.");
        }

        RateLimitResult result = rateLimitingService.tryConsume(apiKey, rateLimited.endpointName());
        if (!result.allowed()) {
            throw new RateLimitException(
                    "Rate limit exceeded. Retry after " + result.retryAfterSeconds() + " seconds.",
                    result
            );
        }

        if (response != null) {
            RateLimitHeaders.applyTo(response, result);
        }

        return joinPoint.proceed();
    }
}