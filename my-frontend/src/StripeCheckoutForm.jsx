import React, { useState } from "react";
import { CardElement, useStripe, useElements } from "@stripe/react-stripe-js";
import { createPaymentIntent } from "./api";

export default function StripeCheckoutForm() {
  const stripe = useStripe();
  const elements = useElements();

  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  const handlePayment = async (e) => {
    e.preventDefault();

    if (!stripe || !elements) {
      setMessage("Stripe has not loaded yet...");
      return;
    }

    setLoading(true);
    setMessage("");

    try {
      // 👉 Step 1: Call backend to get clientSecret
      const res = await createPaymentIntent();

      if (!res.clientSecret) {
        setMessage("No clientSecret returned from backend");
        setLoading(false);
        return;
      }

      const clientSecret = res.clientSecret;

      // 👉 Step 2: Confirm card payment with Stripe Elements
      const cardElement = elements.getElement(CardElement);

      const result = await stripe.confirmCardPayment(clientSecret, {
        payment_method: {
          card: cardElement,
          billing_details: {
            name: "Test User",
          },
        },
      });

      if (result.error) {
        setMessage(result.error.message);
      } else if (result.paymentIntent.status === "succeeded") {
        setMessage("Payment successful! 🎉");
      }

    } catch (err) {
      setMessage(err.message);
    }

    setLoading(false);
  };

  return (
    <div style={{ width: 400, margin: "0 auto" }}>
      <h2>Card Payment</h2>

      <form onSubmit={handlePayment}>
        <div style={{ border: "1px solid #ccc", padding: 10 }}>
          <CardElement />
        </div>

        <button type="submit" disabled={!stripe || loading}
                style={{ marginTop: 20 }}>
          {loading ? "Processing..." : "Pay Now"}
        </button>
      </form>

      {message && <p style={{ marginTop: 20 }}>{message}</p>}
    </div>
  );
}
