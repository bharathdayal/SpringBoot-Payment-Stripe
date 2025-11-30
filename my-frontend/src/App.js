// src/App.jsx
import { BrowserRouter as Router, Routes, Route, NavLink } from "react-router-dom";
import { Elements } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";

import StripeCheckoutForm from "./StripeCheckoutForm";
import SuccessPage from "./SuccessPage";
import PaymentStatusPage from "./PaymentStatusPage";
import CheckoutPage from "./CheckoutPage";

import "./App.css"; // header styles

const stripePromise = loadStripe(
  "pk_test_555xxxxxxxxxxxxxxxxxxxx"
);

export default function App() {
  return (
    <Router>
      {/* HEADER */}
      <header className="header">
        <div className="header-container">

          {/* Project Info */}
          <div>
            <h1 className="project-title">Spring Boot • Stripe Payments System</h1>
            <p className="project-subtitle">
              Stripe Checkout • PaymentIntent • Redis Idempotency • Webhooks • Transaction History
            </p>
          </div>

          {/* Navigation Tabs */}
          <nav className="nav">
            <NavLink
              to="/"
              end
              className={({ isActive }) =>
                "nav-item" + (isActive ? " nav-active" : "")
              }
            >
              Home
            </NavLink>

            <NavLink
              to="/checkout"
              className={({ isActive }) =>
                "nav-item" + (isActive ? " nav-active" : "")
              }
            >
              Intent
            </NavLink>

            <NavLink
              to="/history"
              className={({ isActive }) =>
                "nav-item" + (isActive ? " nav-active" : "")
              }
            >
              History
            </NavLink>
          </nav>
        </div>
      </header>

      {/* MAIN */}
      <main className="main-body">
        <Routes>
          <Route path="/" element={<CheckoutPage />} />

          <Route
            path="/checkout"
            element={
              <Elements stripe={stripePromise}>
                <StripeCheckoutForm />
              </Elements>
            }
          />

          <Route path="/history" element={<PaymentStatusPage />} />
          <Route path="/success" element={<SuccessPage />} />
        </Routes>
      </main>
    </Router>
  );
}
