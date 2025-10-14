package com.example.payment_process.service;

import com.example.payment_process.design.Logger;
import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import com.example.payment_process.model.Payment;
import com.example.payment_process.model.Transaction;
import com.example.payment_process.repository.PaymentRepository;
import com.example.payment_process.repository.TransactionRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service("stripeService")
@RequiredArgsConstructor
public class StripeServiceImpl implements PaymentService {

    Logger log = Logger.getInstance();

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;

    @Value("${stripe.api-key}")
    private String stripeApiKey;


    // ---------------------------
    // PaymentIntent flow
    // ---------------------------
    @Override
    public PaymentResponse createPayment(OrderRequest orderRequest, String idempotencyKey) {

        // Set Stripe API key
        com.stripe.Stripe.apiKey = stripeApiKey;

        // Prepare endpoint-specific key: avoids collisions with checkout sessions
        String intentKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey + "-intent"
                : UUID.randomUUID().toString() + "-intent";

        // 1) Try load existing payment by UUID (idempotency)
        Optional<Payment> existingOpt = paymentRepository.findByUuid(intentKey);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            return PaymentResponse.builder()
                    .success(true)
                    .paymentId(String.valueOf(existing.getId()))
                    //.clientSecret(existing.getClientSecret())
                    .message("Payment already exists for idempotency key")
                    .build();
        }

        // 2) Create Payment record (persist uuid = intentKey)
        Payment payment = Payment.builder()
                .uuid(intentKey)
                .amount(orderRequest.getAmount())
                .currency(orderRequest.getCurrency())
                .description(orderRequest.getDescription())
                .status("CREATED")
                .build();

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException dive) {
            // race: another request inserted same uuid — load it
            payment = paymentRepository.findByUuid(intentKey)
                    .orElseThrow(() -> new RuntimeException("Failed to create or load payment after race"));
        }

        // 3) Build PaymentIntent params
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(orderRequest.getAmount())
                .setCurrency(orderRequest.getCurrency())
                .addPaymentMethodType("card")
                .setDescription(orderRequest.getDescription())
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(intentKey)
                .build();

        try {
            // 4) Create PaymentIntent at Stripe
            PaymentIntent intent = PaymentIntent.create(params, requestOptions);

            // 5) Persist Transaction
            Transaction tx = Transaction.builder()
                    .payment(payment)
                    .uuid(UUID.randomUUID().toString())
                    .gatewayTransactionId(intent.getId())
                    .gateway("STRIPE")
                    .status(intent.getStatus())
                    .build();
            transactionRepository.save(tx);

            // 6) Save clientSecret / status on Payment for later retrieval
           // payment.setClientSecret(intent.getClientSecret());
            payment.setStatus(intent.getStatus());
            paymentRepository.save(payment);

            return PaymentResponse.builder()
                    .success(true)
                    .paymentId(String.valueOf(payment.getId()))
                    .clientSecret(intent.getClientSecret())
                    .message("PaymentIntent created")
                    .build();

        } catch (StripeException e) {
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            return PaymentResponse.builder()
                    .success(false)
                    .paymentId(String.valueOf(payment.getId()))
                    .message("Stripe error: " + e.getMessage())
                    .build();
        }
    }
    // -----------------------------
    // New Stripe Checkout flow
    // -----------------------------
    public String createCheckoutSession(OrderRequest orderRequest, String idempotencyKey)  {

        // Prepare endpoint-specific key: avoids collisions with PaymentIntent keys
        String checkoutKey = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey + "-checkout"
                : UUID.randomUUID().toString() + "-checkout";

        // 1) If payment already exists for this checkoutKey, return existing checkoutUrl if present
        Optional<Payment> existingOpt = paymentRepository.findByUuid(checkoutKey);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            if (existing.getCheckoutUrl() != null && !existing.getCheckoutUrl().isBlank()) {
                return existing.getCheckoutUrl();
            }
        }

        // 2) Create Payment record if missing
        Payment payment = Payment.builder()
                .uuid(checkoutKey)
                .amount(orderRequest.getAmount())
                .currency(orderRequest.getCurrency())
                .description(orderRequest.getDescription())
                .status("CHECKOUT_CREATED")
                .build();

        try {
            payment = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException dive) {
            payment = paymentRepository.findByUuid(checkoutKey)
                    .orElseThrow(() -> new RuntimeException("Failed to create or load payment after race"));
        }

        // 3) Build Checkout Session params
        SessionCreateParams.LineItem.PriceData.ProductData productData =
                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                        .setName(orderRequest.getDescription())
                        .build();

        SessionCreateParams.LineItem.PriceData priceData =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(orderRequest.getCurrency())
                        .setUnitAmount(orderRequest.getAmount())
                        .setProductData(productData)
                        .build();

        SessionCreateParams.LineItem lineItem =
                SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(priceData)
                        .build();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("http://localhost:8086/success?paymentId=" + payment.getId())
                .setCancelUrl("http://localhost:8086/cancel")
                .addLineItem(lineItem)
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setIdempotencyKey(checkoutKey)
                .build();

        // 4) Create Session
        Session session = null;
        try {
            session = Session.create(params, requestOptions);
        } catch (StripeException e) {
            throw new RuntimeException(e);
        }

        // 5) Persist Transaction for the session
        Transaction tx = Transaction.builder()
                .payment(payment)
                .uuid(UUID.randomUUID().toString())
                .gatewayTransactionId(session.getId())
                .gateway("STRIPE_CHECKOUT")
                .status("PENDING")
                .build();
        transactionRepository.save(tx);

        // 6) Save checkout URL on payment and return it
        payment.setCheckoutUrl(session.getUrl());
        paymentRepository.save(payment);

        return session.getUrl();
    }

}
