package com.example.payment_process.service;

import com.example.payment_process.design.Logger;
import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service("idempotentStripeService")
@RequiredArgsConstructor
public class IdempotentStripeService implements PaymentService{

    Logger log = Logger.getInstance();

    //@Qualifier("stripeService")
    private final PaymentService stripeService;
    private final IdempotencyService idempotencyService;
    @Value("${app.idempotency.ttl-seconds:86400}")
    private long ttlSeconds;

    @Override
    public PaymentResponse createPayment(OrderRequest request, String idempotencyKey) {

        // 1) Check Redis first
        PaymentResponse cached = idempotencyService.getResponse(idempotencyKey);
        if (cached != null) {
            log.info("Idempotency key {} found in Redis, returning cached response", idempotencyKey);
            return cached;
        }

        log.info("Idempotency key {} not found, calling StripeService", idempotencyKey);

        // 2) Call actual Stripe service
        PaymentResponse response = stripeService.createPayment(request, idempotencyKey);
        stripeService.createCheckoutSession(request,idempotencyKey);

        log.info("Storing response in Redis for key {}", idempotencyKey);
        // 3) Store in Redis
        idempotencyService.storeResponse(idempotencyKey, response, Duration.ofMinutes(5));

        return response;
    }

    @Override
    public String createCheckoutSession(OrderRequest request, String idempotencyKey) {
        return "";
    }


}
