package com.example.payment_process.controller;

import com.example.payment_process.component.ResolveFrontendBaseUrl;
import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import com.example.payment_process.dto.PaymentSummary;
import com.example.payment_process.model.Payment;
import com.example.payment_process.model.Transaction;
import com.example.payment_process.repository.PaymentRepository;
import com.example.payment_process.repository.TransactionRepository;
import com.example.payment_process.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {


    private final PaymentService stripeService;
    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final ResolveFrontendBaseUrl resolveFrontendBaseUrl;

    public PaymentController(@Qualifier("idempotentStripeService") PaymentService stripeService, PaymentRepository paymentRepository,
                             TransactionRepository transactionRepository,ResolveFrontendBaseUrl resolveFrontendBaseUrl) {
        this.stripeService = stripeService;
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
        this.resolveFrontendBaseUrl=resolveFrontendBaseUrl;
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
            @RequestHeader(value="Idempotency-Key",required = false) String idempotencyKey,
            HttpServletRequest request) throws StripeException {

        String baseUrl = resolveFrontendBaseUrl.frontendBaseUrl(request);

        String checkoutUrl = stripeService.createCheckoutSession(orderRequest,idempotencyKey,baseUrl);
        return ResponseEntity.ok(Map.of("checkoutUrl", checkoutUrl,
                "message", "Stripe Checkout session created successfully"));
    }

    @PostMapping("/webhook/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String sigHeader,
            @RequestBody String payload) {


        stripeService.handleWebhook(sigHeader,payload);


        // You can also handle "payment_intent.succeeded" here if needed

        return ResponseEntity.ok("");
    }

    @GetMapping("/status/{paymentUuid}")
    public ResponseEntity<PaymentResponse>getPaymentStatus(@PathVariable String paymentUuid) {
        PaymentResponse response =stripeService.getPaymentStatus(paymentUuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<List<PaymentSummary>>listAllPayments() {
        return ResponseEntity.ok(stripeService.listAllPayments());
    }
}
