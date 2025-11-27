# 💳 Spring Boot + Stripe Payments + Checkout + Redis Idempotency + Webhooks

This project demonstrates a complete Stripe payment flow using Spring Boot.  
It supports both PaymentIntent (custom card entry) and Checkout (Stripe-hosted page).  
Redis ensures idempotency, preventing duplicate charges.  
Webhooks automatically update payment and transaction status.  
All features are protected using Basic Auth.

---

# 📌 API Endpoints

## 🔹 PaymentIntent (Custom Card Form)
**POST /api/payment/stripe/create**

Creates Payment + PaymentIntent and returns `clientSecret`.

## 🔹 Checkout Session (Hosted Stripe Page)
**POST /api/payment/stripe/checkout**

Creates a Checkout Session and returns `checkoutUrl` for redirect.

## 🔹 Stripe Webhook
**POST /api/payment/webhook/stripe**

Stripe sends events → backend updates Payment + Transaction status.

---

# 🏗 Architecture (Simplified)
- Client calls payment APIs  
- Spring Boot processes requests  
- Stripe SDK creates PaymentIntents or Checkout Sessions  
- Redis stores idempotency keys to prevent duplicates  
- Database stores Payment + Transaction records  
- Stripe Webhooks notify the backend of final payment status  

Client App
│
├── Calls /payment/create → PaymentIntent (clientSecret returned)
│
├── Calls /payment/checkout → Stripe Checkout Session (URL returned)
│
Spring Boot Backend
│
├── Stripe SDK → Creates PaymentIntent / Checkout Session
│
├── Redis (idempotency keys)
│ │
│ └── Prevents duplicate payment creation
│
├── Database
│ └── Stores Payment + Transaction records
│
└── Receives Stripe Webhooks
│
└── Updates payment status (SUCCEEDED / FAILED)

---

# 💰 Payment Flows

## 1️⃣ PaymentIntent Flow (Custom Card Form)
- Client sends POST **/api/payment/stripe/create**  
- Backend creates Payment + PaymentIntent  
- Backend returns `clientSecret`  
- Frontend uses Stripe.js to confirm payment  
- Stripe processes card  
- Stripe sends webhook `payment_intent.succeeded`  
- Backend updates Payment + Transaction → **SUCCEEDED**  

## 2️⃣ Checkout Session Flow (Stripe Hosted Page)
- Client sends POST **/api/payment/stripe/checkout**  
- Backend creates Payment + Checkout Session  
- Backend returns `checkoutUrl`  
- Browser redirects to Stripe Checkout  
- User enters card details and completes payment  
- Stripe sends webhook `checkout.session.completed`  
- Backend updates Payment + Transaction → **PAYMENT_SUCCEEDED**  

---

# 🔁 Redis Idempotency Flow
- Client includes header: `Idempotency-Key: <uuid>`  
- Backend checks Redis for existing response  
- If found → returns cached response  
- If not → creates PaymentIntent or Checkout Session  
- Keys stored as:  
  - `<uuid>-intent`  (PaymentIntent)  
  - `<uuid>-checkout` (Checkout Session)  
- Prevents duplicate charges across retries or double-clicks  

---

# 🌐 Webhook Flow (Automatic Payment Confirmation)
- Stripe sends events to **/api/payment/webhook/stripe**:  
  - `payment_intent.succeeded`  
  - `payment_intent.payment_failed`  
  - `checkout.session.completed`  
- Backend retrieves Stripe transaction ID  
- Updates Transaction → SUCCEEDED / FAILED  
- Updates Payment → PAYMENT_SUCCEEDED / PAYMENT_FAILED  
- Ensures final state correctness even if frontend fails to redirect  

---

# 🔐 Security Flow (Basic Auth)
- All payment endpoints require HTTP Basic Auth  
- Only authenticated users can call:  
  - **/api/payment/stripe/create**  
  - **/api/payment/stripe/checkout**  
- Protects payment APIs from unauthorized requests  

---

# 🧪 Running the Application
- Start Redis:  
  `docker run -d -p 6379:6379 redis`  
- Start Spring Boot:  
  `./gradlew bootRun`  
- Use Basic Auth (admin/admin) and always supply `Idempotency-Key`  

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

## 🗄️ Example Data Stored

### Payment Table

| Payment ID | Amount | Date                       | Currency | Description                | Status                   | UUID                                 | Checkout URL                                      |
|------------|--------|----------------------------|----------|----------------------------|--------------------------|--------------------------------------|---------------------------------------------------|
| 1          | 60000  | 2025-10-14                 | usd      | Order #123 - Iphone 16     | requires_payment_method  | 3a67c3f4-39a2-4c02-b7b9-1D-intent    |                                                   |
| 2          | 60000  | 2025-10-14                 | usd      | Order #123 - Iphone 16     | CHECKOUT_CREATED         | 3a67c3f4-39a2-4c02-b7b9-1D-checkout  | https://checkout.stripe.com/c/pay/cs_test_a1xxxxxx |
| 2          | 60000  | 2025-10-14                 | usd      | Order #123 - Iphone 16     | PAYMENT_SUCCEEDED        | 3a67c3f4-39a2-4c02-b7b9-1D-checkout  |

### Transaction Table

| Gateway         | Transaction ID         | Status                     | UUID                                PaymentID  | 
|-----------------|-----------------------|---------                    |-----------------------------        |
| STRIPE          | cs_test_a1xxxxx       | requires_payment_method     | cbabbdab-2c5b-4ec3-ab3e-33         |1
| STRIPE_CHECKOUT | cs_test_a1xxxxx       | PENDING                     | 68a2d6e5-4050-4ac1-90f3-65          |2
| STRIPE_CHECKOUT | cs_test_a1xxxxx       | SUCCEEDED                   | 68a2d6e5-4050-4ac1-90f3-65          |2

---

---

# 🎯 Summary
- Supports PaymentIntent + Checkout flows  
- Redis guarantees safe, duplicate-proof payments  
- Database stores complete payment lifecycle  
- Stripe Webhooks finalize payment status  
- Secured using Spring Security  
- Clean, scalable, production-ready payment architecture 
