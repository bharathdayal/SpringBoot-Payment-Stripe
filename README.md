💳 Spring Boot + Stripe Payments + Stripe Checkout + Redis Idempotency + Webhooks

A complete Spring Boot demo that implements:

Stripe PaymentIntent API (custom card input using Stripe.js)

Stripe Checkout Session API (hosted checkout page)

Redis-based idempotency to prevent duplicate charging

Payment & Transaction persistence using JPA/Hibernate

Webhook-based automatic payment confirmation

Spring Security (Basic Auth) for protected endpoints

This project is ideal for learning or integrating Stripe payments into microservices or monolithic Spring Boot applications.

📘 Table of Contents

Overview

Architecture

Tech Stack

Dependencies

Configuration

Database Entities

Redis Idempotency

Stripe Services

Security (Basic Auth)

Controllers

Webhook — Automatic Payment Confirmation

Running the Application

Payment Flows

Future Enhancements

Author

🧠 Overview

This Spring Boot application demonstrates:

✔ Creating PaymentIntents for card payments
✔ Redirecting users to Stripe Checkout
✔ Enforcing idempotent payment creation using Redis
✔ Storing payments and transactions in a relational DB
✔ Automatic payment reconciliation using Stripe Webhooks
✔ Securing API endpoints with Basic Auth

🏗 Architecture
Client → Spring Boot API → Stripe SDK → Stripe Gateway
           │                    │
           │                    ├── PaymentIntent / CheckoutSession
           │                    └── clientSecret / checkoutUrl
           │
           ├── Redis (idempotency)
           └── Database (Payment + Transaction)
                       ↑
                       │
              Stripe Webhook → Automatic Confirmation

⚙ Tech Stack
Category	Technology
Backend	Spring Boot 3.x
Language	Java 17+
Payment Gateway	Stripe Java SDK
Cache	Redis
Database	H2 / MySQL / PostgreSQL
ORM	Hibernate / Spring Data JPA
Security	Spring Security (Basic Auth)
Build	Gradle
📦 Dependencies (Gradle)
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'com.stripe:stripe-java:24.10.0'
    runtimeOnly 'com.h2database:h2'    // or MySQL/PostgreSQL driver
}

⚙ Configuration

Create application.properties:

server.port=8080

# Stripe
stripe.api.key=sk_test_xxx
stripe.webhook.secret=whsec_xxx

# Database
spring.datasource.url=jdbc:h2:mem:paymentdb
spring.jpa.hibernate.ddl-auto=update

# Redis
spring.redis.host=localhost
spring.redis.port=6379

🧩 Database Entities
Payment.java
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uuid;        // idempotency key or session key
    private Long amount;
    private String currency;
    private String description;
    private String status;

    @Column(length = 2000)
    private String clientSecret;

    @Column(length = 2000)
    private String checkoutUrl;
}

Transaction.java
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uuid;
    private String gatewayTransactionId;
    private String gateway;
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    private Payment payment;
}

🔁 Redis Idempotency
IdempotencyService
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String PREFIX = "idem:";

    public void storeResponse(String key, PaymentResponse response, Duration ttl) {
        redisTemplate.opsForValue().set(PREFIX + key,
                mapper.writeValueAsString(response), ttl);
    }

    public PaymentResponse getResponse(String key) {
        Object json = redisTemplate.opsForValue().get(PREFIX + key);
        if (json == null) return null;
        try {
            return mapper.readValue(json.toString(), PaymentResponse.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

💼 Stripe Services

The service includes two flows:

1️⃣ PaymentIntent Flow (createPayment)

Suitable for custom card forms using Stripe.js

Returns clientSecret

Uses idempotency key with -intent suffix

2️⃣ Checkout Session Flow (createCheckoutSession)

Redirects user to Stripe Checkout

Stores the checkoutUrl

Uses idempotency key with -checkout suffix

🔐 Security (Basic Auth)
@Bean
public UserDetailsService userDetailsService() {
    UserDetails admin = User.withUsername("admin")
            .password("{noop}admin")
            .roles("ADMIN")
            .build();
    return new InMemoryUserDetailsManager(admin);
}

@Bean
public SecurityFilterChain security(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/payment/**").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .httpBasic();
    return http.build();
}

🧾 Controllers
POST /api/payment/stripe/create

Creates a Stripe PaymentIntent and returns the client secret.

POST /api/payment/stripe/checkout

Creates a Stripe Checkout Session and returns checkoutUrl to redirect the user.

🌐 Webhook — Automatic Payment Confirmation

Stripe sends events when:

PaymentIntent succeeded

PaymentIntent failed

Checkout Session completed

Webhook Endpoint
@PostMapping("/webhook/stripe")
public ResponseEntity<String> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {

    Event event;

    try {
        event = Webhook.constructEvent(
                payload,
                sigHeader,
                webhookSecret
        );
    } catch (Exception e) {
        return ResponseEntity.badRequest().build();
    }

    switch (event.getType()) {

        case "payment_intent.succeeded" -> {
            PaymentIntent intent =
                (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);

            if (intent != null) {
                updatePaymentStatus(intent.getId(), "SUCCEEDED");
            }
        }

        case "payment_intent.payment_failed" -> {
            PaymentIntent intent =
                (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);
            if (intent != null) {
                updatePaymentStatus(intent.getId(), "FAILED");
            }
        }

        case "checkout.session.completed" -> {
            Session session =
                (Session) event.getDataObjectDeserializer()
                    .getObject().orElse(null);

            if (session != null && session.getPaymentIntent() != null) {
                updatePaymentStatus(session.getPaymentIntent(), "SUCCEEDED");
            }
        }
    }

    return ResponseEntity.ok("");
}

🔄 Payment + Transaction Auto-Update Logic
private void updatePaymentStatus(String paymentIntentId, String newStatus) {

    Transaction tx = transactionRepository
            .findByGatewayTransactionId(paymentIntentId)
            .orElse(null);

    if (tx == null) {
        System.out.println("No transaction found for PaymentIntent " + paymentIntentId);
        return;
    }

    tx.setStatus("SUCCEEDED");
    transactionRepository.save(tx);

    Payment payment = tx.getPayment();
    if (payment != null) {
        payment.setStatus("PAYMENT_SUCCEEDED");
        paymentRepository.save(payment);

        System.out.println("Updated Payment id=" + payment.getId()
                + " and Transaction id=" + tx.getId());
    } else {
        System.out.println("Transaction " + tx.getId()
                + " has no associated Payment");
    }
}

🧪 Running the Application
Start Redis
docker run -d -p 6379:6379 redis

Run Spring Boot
./gradlew bootRun

💰 Payment Flows
1️⃣ PaymentIntent (Custom Card Form)

Client calls /stripe/create

Backend creates PaymentIntent

Returns clientSecret

Frontend confirms payment using stripe.confirmCardPayment

Webhook updates DB (Payment + Transaction)

2️⃣ Checkout Session (Stripe Hosted)

Client calls /stripe/checkout

Backend returns checkoutUrl

Frontend redirects user to Stripe

User enters card details

Stripe processes payment

Webhook updates Payment + Transaction
