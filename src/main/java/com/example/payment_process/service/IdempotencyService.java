package com.example.payment_process.service;

import com.example.payment_process.dto.PaymentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String,Object> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String PREFIX = "idem:";

    public void storeResponse(String key, PaymentResponse response, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(PREFIX + key, json, ttl);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize response for idempotency", e);
        }
    }


    public PaymentResponse getResponse(String key) {
        Object val = redisTemplate.opsForValue().get(PREFIX + key);
        if (val == null) return null;
        try {
            return objectMapper.readValue(val.toString(), PaymentResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize idempotent response", e);
        }
    }

    public void storeCheckoutUrl(String key, String checkoutUrl, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + key, checkoutUrl, ttl);
    }

    public String getCheckoutUrl(String key) {
        Object val = redisTemplate.opsForValue().get(PREFIX + key);
        return val != null ? val.toString() : null;
    }

}
