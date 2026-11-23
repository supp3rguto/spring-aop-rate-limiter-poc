package com.backend.ratelimiter.exception;

import com.backend.ratelimiter.aop.RateLimitHeaders;
import com.backend.ratelimiter.service.RateLimitResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitException(RateLimitException ex) {
        RateLimitResult result = ex.getResult();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Rate Limit Exceeded");
        problem.setType(URI.create("urn:rate-limiter:rate-limit-exceeded"));
        problem.setProperty("limit", result.limit());
        problem.setProperty("remaining", result.remaining());
        problem.setProperty("resetAt", result.resetAtEpochSeconds());
        problem.setProperty("retryAfterSeconds", result.retryAfterSeconds());

        HttpHeaders headers = RateLimitHeaders.toHttpHeaders(result);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(problem);
    }

    @ExceptionHandler(ApiKeyInvalidException.class)
    public ResponseEntity<ProblemDetail> handleApiKeyInvalidException(ApiKeyInvalidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Unauthorized API Key");
        problem.setType(URI.create("urn:rate-limiter:invalid-api-key"));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed."
        );
        problem.setTitle("Invalid Request");
        problem.setType(URI.create("urn:rate-limiter:validation-error"));
        problem.setProperty("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableMessage(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed or missing JSON request body."
        );
        problem.setTitle("Malformed Request");
        problem.setType(URI.create("urn:rate-limiter:malformed-request"));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}