package com.example.payment_process.service;

import com.example.payment_process.design.Logger;
import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import com.example.payment_process.dto.PaymentSummary;
import com.example.payment_process.model.Payment;
import com.example.payment_process.model.Transaction;
import com.example.payment_process.repository.PaymentRepository;
import com.example.payment_process.repository.TransactionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
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

    @Value("${stripe.api-webhook-secret}")
    private String stripeWebhookSecret;


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
    public String createCheckoutSession(OrderRequest orderRequest, String idempotencyKey,String baseUrl)  {

        // Set Stripe API key
        com.stripe.Stripe.apiKey = stripeApiKey;

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
                .setSuccessUrl(baseUrl + "/success?paymentId=" + payment.getId())
                .setCancelUrl(baseUrl + "/cancel")
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

    @Override
    public String handleWebhook(String sigHeader, String payload) {
        Event event;
        try {
            event = Webhook.constructEvent(
                    payload,
                    sigHeader,
                    stripeWebhookSecret
            );
        } catch (SignatureVerificationException e) {
            throw new RuntimeException(e);
        }

        String eventType = event.getType();
        log.msg("Stripe event type = " + eventType);

        if ("checkout.session.completed".equals(eventType) || "payment_intent.succeeded".equals(eventType)) {
            handleCheckoutSessionCompleted(event);
        }
        return "OK";
    }



    private void handleCheckoutSessionCompleted(Event event) {

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Session session = null;

        // 1) Try normal deserialization
        if (deserializer.getObject().isPresent()) {
            StripeObject stripeObject = deserializer.getObject().get();
            if (stripeObject instanceof Session) {
                session = (Session) stripeObject;
            } else {
                log.msg("Expected Session, but got " +
                        stripeObject.getClass().getName());
            }
        } else {
            // 2) Fallback: parse raw JSON as Session
            String rawJson = deserializer.getRawJson();
            log.msg("Using raw JSON fallback: " + rawJson);
            session = ApiResource.GSON.fromJson(rawJson, Session.class);
        }

        if (session == null) {
            log.msg("Could not deserialize Session for event " + event.getId());
            return; // nothing to update, but controller will still return 200
        }

        String sessionId = session.getId();
        log.msg("checkout.session.completed for sessionId = " + sessionId);

        // 3) Load existing Transaction from DB
        Optional<Transaction> txOpt =
                transactionRepository.findByGatewayTransactionId(sessionId);

        if (txOpt.isEmpty()) {
            log.msg("No Transaction found for gatewayTransactionId = " + sessionId);
            return;
        }

        Transaction tx = txOpt.get();

        // 4) Update transaction status
        tx.setStatus("SUCCEEDED");
        transactionRepository.save(tx);

        // 5) Update linked Payment
        Payment payment = tx.getPayment();
        if (payment != null) {
            payment.setStatus("PAYMENT_SUCCEEDED");
            paymentRepository.save(payment);
            log.msg("Updated Payment id=" + payment.getId()
                    + " and Transaction id=" + tx.getId());
        } else {
            log.msg("Transaction " + tx.getId() +
                    " has no associated Payment. Check mapping.");
        }
    }

    @Override
    public PaymentResponse getPaymentStatus(String paymentUuid) {
        return paymentRepository.findByUuid(paymentUuid)
                .map(payment -> {

                    // get latest transaction for this payment (if any)
                    var transactions =
                            transactionRepository.findByPaymentOrderByCreatedAtDesc(payment);

                    Transaction latestTx = transactions.isEmpty() ? null : transactions.get(0);

                    return PaymentResponse.builder()
                            .success(true)
                            .message("Payment status fetched successfully")
                            .paymentUuid(payment.getUuid())
                            .amount(payment.getAmount())
                            .currency(payment.getCurrency())
                            .productDesc(payment.getDescription())
                            .status(payment.getStatus())
                            .lastTransactionUuid(latestTx != null ? latestTx.getUuid() : null)
                            .lastTransactionStatus(latestTx != null ? latestTx.getStatus() : null)
                            .lastGateway(latestTx != null ? latestTx.getGateway() : null)
                            .lastGatewayTransactionId(
                                    latestTx != null ? latestTx.getGatewayTransactionId() : null
                            )
                            .lastTransactionCreatedAt(
                                    latestTx != null ? latestTx.getCreatedAt() : null
                            )
                            .build();
                })
                .orElseGet(() -> PaymentResponse.builder()
                        .success(false)
                        .message("No payment found for uuid=" + paymentUuid)
                        .paymentUuid(paymentUuid)
                        .build());
    }

    @Override
    public PaymentResponse getPaymentStatus(Long orderId) {
        return paymentRepository.findById(orderId)
                .map(payment -> {

                    // get latest transaction for this payment (if any)
                    var transactions =
                            transactionRepository.findByPaymentOrderByCreatedAtDesc(payment);

                    Transaction latestTx = transactions.isEmpty() ? null : transactions.get(0);

                    return PaymentResponse.builder()
                            .success(true)
                            .message("Payment status fetched successfully")
                            .paymentUuid(payment.getUuid())
                            .amount(payment.getAmount())
                            .currency(payment.getCurrency())
                            .productDesc(payment.getDescription())
                            .status(payment.getStatus())
                            .lastTransactionUuid(latestTx != null ? latestTx.getUuid() : null)
                            .lastTransactionStatus(latestTx != null ? latestTx.getStatus() : null)
                            .lastGateway(latestTx != null ? latestTx.getGateway() : null)
                            .lastGatewayTransactionId(
                                    latestTx != null ? latestTx.getGatewayTransactionId() : null
                            )
                            .lastTransactionCreatedAt(
                                    latestTx != null ? latestTx.getCreatedAt() : null
                            )
                            .build();
                })
                .orElseGet(() -> PaymentResponse.builder()
                        .success(false)
                        .message("No payment found for orderId=" + orderId)
                        .build());
    }

    @Override
    public List<PaymentSummary> listAllPayments() {
        return paymentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(p -> PaymentSummary.builder()
                        .uuid(p.getUuid())
                        .amount(p.getAmount())
                        .currency(p.getCurrency())
                        .status(p.getStatus())
                        .createdAt(p.getCreatedAt())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

}
