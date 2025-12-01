package com.example.payment_process.service;

import com.example.payment_process.design.Logger;
import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import com.example.payment_process.dto.PaymentSummary;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

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


        log.info("Storing response in Redis for key {}", idempotencyKey);
        // 3) Store in Redis
        idempotencyService.storeResponse(idempotencyKey, response, Duration.ofMinutes(5));

        return response;
    }

    @Override
    public String createCheckoutSession(OrderRequest request, String idempotencyKey,String baseUrl) {

        // If no idempotency key, just delegate directly
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            log.info("No idempotency key {} provided for checkout delegating directly to StripeService",idempotencyKey);
            return stripeService.createCheckoutSession(request, null,"");
        }

        // Use a separate logical key for checkout so it doesn't clash with PaymentIntent
        String checkoutKey = idempotencyKey + "-checkout";

        // 1) Check Redis first
        String cachedUrl = idempotencyService.getCheckoutUrl(checkoutKey);

        if (cachedUrl != null) {
            log.info("Checkout idempotency key {} found in Redis, returning cached checkout URL", checkoutKey);
            return cachedUrl;
        }

        log.info("Checkout idempotency key {} not found, calling StripeService.createCheckoutSession", checkoutKey);

        // 2) Call actual Stripe checkout flow
        String checkoutUrl = stripeService.createCheckoutSession(request, idempotencyKey,baseUrl);

        // 3) Store in Redis
        log.info("Storing checkout URL in Redis for key {}", checkoutKey);
        idempotencyService.storeCheckoutUrl(checkoutKey, checkoutUrl, Duration.ofMinutes(5));

        return checkoutUrl;
    }

    @Override
    public String handleWebhook(String sigHeader, String payload) {

        // 1) Validate header
        if (sigHeader == null || sigHeader.isBlank()) {
            log.msg("Missing Stripe-Signature header");
            return "Missing signature";
        }

        // 2) Validate payload
        if (payload == null || payload.isBlank()) {
            log.msg("Empty webhook payload");
            return "Empty payload";
        }

        return stripeService.handleWebhook(sigHeader, payload);

    }

    @Override
    public PaymentResponse getPaymentStatus(String uuId) {
        if (uuId == null || uuId.isBlank()) {
            log.msg("Missing UUID ID");
            return null;
        }
        return stripeService.getPaymentStatus(uuId);
    }

    @Override
    public PaymentResponse getPaymentStatus(Long orderId) {
        if (orderId == null) {
            log.msg("Missing Order ID");
            return null;
        }
        return stripeService.getPaymentStatus(orderId);
    }

    @Override
    public List<PaymentSummary> listAllPayments() {
        return stripeService.listAllPayments();
    }


}
