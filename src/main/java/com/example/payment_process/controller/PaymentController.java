package com.example.payment_process.controller;

import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import com.example.payment_process.service.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {


    private final PaymentService stripeService;
    public PaymentController(@Qualifier("idempotentStripeService") PaymentService stripeService) {
        this.stripeService = stripeService;
    }

    @PostMapping("/create")
    public ResponseEntity<PaymentResponse>createStripePayment(@Valid @RequestBody OrderRequest request,
                                                              @RequestHeader(value="Idempotency-Key",required = false) String idempotencyKey) {
        PaymentResponse response=stripeService.createPayment(request,idempotencyKey);
        return response.isSuccess() ? ResponseEntity.ok(response):ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // New endpoint for Stripe Checkout
    @PostMapping("/stripe/checkout")
    public ResponseEntity<Map<String, String>> createCheckout(
            @RequestBody OrderRequest orderRequest,
            @RequestHeader(value="Idempotency-Key",required = false) String idempotencyKey) throws StripeException {

        String checkoutUrl = stripeService.createCheckoutSession(orderRequest,idempotencyKey);
        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl,
                "message", "Stripe Checkout session created successfully"));
    }
}
