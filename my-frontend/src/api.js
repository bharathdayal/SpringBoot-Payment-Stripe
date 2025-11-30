const BASE_URL = "http://localhost:8086/api/payment";

export async function fetchPaymentStatus(paymentUuid) {
  const res = await fetch(`${BASE_URL}/status/${paymentUuid}`);
  if (!res.ok) {
    throw new Error(`HTTP error ${res.status}`);
  }
  return res.json(); // matches PaymentResponse from backend
}


export async function fetchPaymentList() {
  const res = await fetch(`${BASE_URL}/list`);
  if (!res.ok) throw new Error(`HTTP error ${res.status}`);
  return res.json(); // List<PaymentSummary>
}

export async function createPaymentIntent(amount = 5000) {
  const order = {
    amount: amount,
    currency: "usd",
    description: "Order #M3 -Alexan Echo Dot",
  };

  
  const res = await fetch(`${BASE_URL}/create`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": crypto.randomUUID(), // optional but recommended
    },
    body: JSON.stringify(order),
  });

  return res.json(); // returns {clientSecret, success, message}
}

export async function createStripeCheckout(orderRequest) {
  const res = await fetch(`${BASE_URL}/stripe/checkout`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      // generate a fresh idempotency key for each click
      "Idempotency-Key":
        typeof crypto !== "undefined" && crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random()}`
    },
    body: JSON.stringify(orderRequest),
  });

  if (!res.ok) {
    const text = await res.text();
    throw new Error(`HTTP ${res.status}: ${text}`);
  }

  // your controller returns: { checkoutUrl: "...", message: "..." }
  return res.json();
}
