package com.backend.ratelimiter.controller;

import com.backend.ratelimiter.service.RateLimitingService;
import com.backend.ratelimiter.exception.RateLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RateLimitingService rateLimitingService;

    @Test
    @DisplayName("Deve retornar 429 Too Many Requests se o limite for estourado")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        // Simula o serviço estourando o limite
        doThrow(new RateLimitException("Limite excedido")).when(rateLimitingService).checkLimit(anyString(), anyInt());

        mockMvc.perform(get("/api/v1/data")
               .header("X-API-KEY", "minha-chave-123"))
               .andExpect(status().isTooManyRequests()); // Valida o status HTTP 429
    }
}