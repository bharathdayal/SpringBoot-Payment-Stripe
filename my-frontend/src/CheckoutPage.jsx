// src/CheckoutPage.jsx
import { useState } from "react";
import { createStripeCheckout } from "./api";
import "./CheckoutPage.css";

export default function CheckoutPage() {
  const [form, setForm] = useState({
    amount: 35000,
    currency: "usd",
    description: "Order #M3 - LG Monitor",
    customerEmail: "test@example.com",
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [info, setInfo] = useState("");

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({
      ...prev,
      [name]: name === "amount" ? Number(value) || "" : value,
    }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setInfo("");
    setLoading(true);

    try {
      // Call backend
      const res = await createStripeCheckout(form);
      const { checkoutUrl, message } = res;
      setInfo(message || "Checkout session created, redirecting...");

      if (checkoutUrl) {
        // Redirect to Stripe-hosted checkout page
        window.location.href = checkoutUrl;
      } else {
        setError("No checkout URL returned from server");
      }
    } catch (err) {
      console.error(err);
      setError(err.message || "Failed to create Stripe Checkout session");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="co-page-wrapper">
      <div className="co-gradient-bg" />

      <div className="co-content">
        <div className="co-card">
          <h1>Start Stripe Checkout</h1>
          <p className="co-subtitle">
            Enter payment details and you’ll be redirected to Stripe’s hosted
            checkout page.
          </p>

          <form onSubmit={handleSubmit} className="co-form">
            <div className="co-field">
              <label>Amount (in cents)</label>
              <input
                type="number"
                name="amount"
                value={form.amount}
                onChange={handleChange}
                min="1"
                required
              />
              <span className="co-hint">
                35000 = 350.00 USD
              </span>
            </div>

            <div className="co-field">
              <label>Currency</label>
              <select
                name="currency"
                value={form.currency}
                onChange={handleChange}
                required
              >
                <option value="usd">USD</option>
                <option value="inr">INR</option>
                <option value="eur">EUR</option>
              </select>
            </div>

            <div className="co-field">
              <label>Description</label>
              <input
                type="text"
                name="description"
                value={form.description}
                onChange={handleChange}
                placeholder="Order #M3 - LG Monitor"
                required
              />
            </div>

            <div className="co-field">
              <label>Customer Email</label>
              <input
                type="email"
                name="customerEmail"
                value={form.customerEmail}
                onChange={handleChange}
                placeholder="customer@example.com"
                required
              />
            </div>

            <button type="submit" disabled={loading}>
              {loading ? "Creating Checkout..." : "Pay with Stripe Checkout"}
            </button>
          </form>

          {error && <div className="co-alert co-alert-error">{error}</div>}
          {info && !error && (
            <div className="co-alert co-alert-info">{info}</div>
          )}

          <p className="co-footer-hint">
            After completing payment, Stripe will redirect you to your{" "}
            <strong>/success</strong> page with <code>paymentId</code> so you
            can display the confirmation and link to payment history.
          </p>
        </div>
      </div>
    </div>
  );
}
