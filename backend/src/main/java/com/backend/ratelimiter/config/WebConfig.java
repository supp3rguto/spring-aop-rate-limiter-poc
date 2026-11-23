package com.backend.ratelimiter.config;

import com.backend.ratelimiter.aop.RateLimitHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(
                        RateLimitHeaders.LIMIT,
                        RateLimitHeaders.REMAINING,
                        RateLimitHeaders.RESET,
                        RateLimitHeaders.RETRY_AFTER
                )
                .allowCredentials(true);
    }
}