// src/SuccessPage.jsx
import { useEffect, useState } from "react";
import { useLocation, Link } from "react-router-dom";
import "./SuccessPage.css";

function useQuery() {
  const { search } = useLocation();
  return new URLSearchParams(search);
}

export default function SuccessPage() {
  const query = useQuery();
  const [paymentId, setPaymentId] = useState("");

  useEffect(() => {
    const id = query.get("paymentId");
    if (id) setPaymentId(id);
  }, [query]);

  return (
    <div className="succ-page-wrapper">
      <div className="succ-gradient-bg" />

      <div className="succ-content">
        <div className="succ-card">
          <div className="succ-icon-circle">
            <span className="succ-icon">✓</span>
          </div>
          <h1>Payment Successful</h1>
          <p className="succ-subtitle">
            Thank you! Your payment has been processed successfully.
          </p>

          {paymentId && (
            <div className="succ-info">
              <span className="succ-label">Payment ID</span>
              <span className="succ-value">{paymentId}</span>
            </div>
          )}

          <div className="succ-actions">
            <Link to="/history" className="succ-btn-primary">
              View Payment History
            </Link>

            <Link to="/" className="succ-btn-secondary">
              Back to Home
            </Link>
          </div>

          <p className="succ-hint">
            You can always view the status and latest transaction details in the{" "}
            <strong>Payment History</strong> page.
          </p>
        </div>
      </div>
    </div>
  );
}
