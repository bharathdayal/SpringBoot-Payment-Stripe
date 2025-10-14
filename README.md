# 💳 Spring Boot + Stripe Checkout + Redis Idempotency Demo

A demo Spring Boot project that integrates **Stripe Payments** and **Stripe Checkout** APIs with **Redis-based idempotency** to prevent duplicate payments.  
It also demonstrates clean architecture with `Service`, `Repository`, `Controller`, and `Model` layers — using JPA and Gradle.

---

## 🧠 Overview

This project shows how to:

- Create **PaymentIntent** or **Checkout Sessions** using Stripe SDK.
- Ensure **idempotent payment operations** via Redis (so the same request key can’t charge twice).
- Persist **Payment** and **Transaction** details in a relational database.
- Expose REST endpoints for integration with any frontend or mobile app.
- Implement **Basic Authentication** for payment APIs.

---

## 🏗️ Architecture Diagram

Client → Spring Boot REST API → Stripe SDK → Stripe Gateway  
│ │  
│ ├── PaymentIntent / CheckoutSession  
│ └── Returns clientSecret / checkoutUrl  
│  
├── Redis (idempotent request keys)  
└── MySQL/Postgres (Payment & Transaction persistence)

---

## ⚙️ Tech Stack

| Layer             | Technology                |
|-------------------|--------------------------|
| Backend Framework | Spring Boot 3.x          |
| Build Tool        | Gradle                   |
| Database          | H2 / MySQL (JPA + Hibernate) |
| Caching           | Redis                    |
| Payment Gateway   | Stripe SDK (Java)        |
| Security          | Spring Security (Basic Auth) |
| Logging           | SLF4J / Logback          |

---

## 📦 Dependencies (Gradle)

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'com.stripe:stripe-java:24.10.0'
    implementation 'com.fasterxml.jackson.core:jackson-databind'
    runtimeOnly 'com.mysql:mysql-connector-j' // or MySQL/Postgres driver
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

---

## 📤 Example Request

**POST** `/api/payment/stripe/create`

**Headers**
```
Authorization: Basic YWRtaW46YWRtaW4=
Idempotency-Key: f7b1b2c6-93d8-4b45-9af8-8f6c4c93e912
```

**Body**
```json
{
  "amount": 5000,
  "currency": "usd",
  "description": "Spring Boot Stripe Demo Payment"
}
```

**Response**
```json
{
  "success": true,
  "paymentId": "1",
  "clientSecret": "pi_3OvD...secret_abc",
  "message": "PaymentIntent created"
}
```

---

## 🔁 Idempotency & Retry Behavior

If you retry the same request with the same Idempotency-Key, the API will respond:

```json
{
    "success": true,
    "paymentId": "3",
    "clientSecret": null,
    "message": "Payment already exists for idempotency key"
}
```

This ensures duplicate payments are not processed.

---

## 💡 Redis Idempotency Flow

- Client sends POST request with Idempotency-Key.
- The key is stored in Redis (`idem:<key>`) along with the serialized response.
- If the same key is sent again → the cached response is returned instantly.
- This prevents duplicate Stripe charges.

---

## 🧠 Stripe Checkout Flow

1. Client → `/api/payment/stripe/checkout`
2. Server → Creates Stripe Session and returns `checkoutUrl`
3. Frontend → Redirects user to Stripe-hosted checkout page
4. User enters card details → Stripe handles secure payment
5. Stripe sends webhook (optional) to your backend confirming payment success/failure

---

## 🗄️ Example Data Stored

### Payment Table

| Payment ID | Amount | Date                       | Currency | Description                | Status                   | UUID                                 | Checkout URL                                      |
|------------|--------|----------------------------|----------|----------------------------|--------------------------|--------------------------------------|---------------------------------------------------|
| 1          | 60000  | 2025-10-14                 | usd      | Order #123 - Iphone 16     | requires_payment_method  | 3a67c3f4-39a2-4c02-b7b9-1D-intent    |                                                   |
| 2          | 60000  | 2025-10-14                 | usd      | Order #123 - Iphone 16     | CHECKOUT_CREATED         | 3a67c3f4-39a2-4c02-b7b9-1D-checkout  | https://checkout.stripe.com/c/pay/cs_test_a1xxxxxx |

### Transaction Table

| Gateway         | Transaction ID         | Status                     | UUID                                PaymentID  | 
|-----------------|-----------------------|---------                    |-----------------------------        |
| STRIPE          | cs_test_a1xxxxx       | requires_payment_method     | cbabbdab-2c5b-4ec3-ab3e-33         |1
| STRIPE_CHECKOUT | cs_test_a1xxxxx       | PENDING                     | 68a2d6e5-4050-4ac1-90f3-65          |2

---

## 🧪 Run Locally

```sh
# Start Redis
docker run -d -p 6379:6379 redis

# Run Spring Boot app
./gradlew bootRun
```

Visit:

- `POST /api/payment/stripe/create` (PaymentIntent)
- `POST /api/payment/stripe/checkout` (Checkout Session)
