package com.backend.ratelimiter.repository;

import com.backend.ratelimiter.model.ApiKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM ApiKey k WHERE k.apiKey = :apiKey")
    Optional<ApiKey> findByIdForUpdate(@Param("apiKey") String apiKey);
}