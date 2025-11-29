// src/PaymentStatusPage.jsx
import { useState } from "react";
import { fetchPaymentStatus } from "./api";
import "./PaymentStatusPage.css";

export default function PaymentStatusPage() {
  const [uuid, setUuid] = useState("");
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleSearch = async () => {
    if (!uuid.trim()) {
      setError("Please enter a Payment UUID");
      return;
    }
    setError("");
    setLoading(true);
    setData(null);

    try {
      const res = await fetchPaymentStatus(uuid.trim());
      setData(res);
      if (!res.success) {
        setError(res.message || "Payment not found");
      }
    } catch (e) {
      console.error(e);
      setError("Failed to fetch payment status");
    } finally {
      setLoading(false);
    }
  };

  const statusBadgePayment = (status) => {
    if (!status) return <span className="ps-badge ps-badge-neutral">-</span>;
    const s = status.toUpperCase();
     if (s === "CHECKOUT_CREATED" ) {
      return <span className="ps-badge ps-badge-success">Checkout Pending</span>;
    }
    if ( s === "PAYMENT_SUCCEEDED") {
      return <span className="ps-badge ps-badge-success">Succeeded</span>;
    }
    
    return <span className="ps-badge ps-badge-warning">Pending</span>;
  };

  const statusBadgeTransaction = (status) => {
    if (!status) return <span className="ps-badge ps-badge-neutral">-</span>;
    const s = status.toUpperCase();
    if (s === "SUCCEEDED" ) {
      return <span className="ps-badge ps-badge-success">Succeeded</span>;
    }
    if (s === "FAILED") {
      return <span className="ps-badge ps-badge-error">Failed</span>;
    }
    return <span className="ps-badge ps-badge-warning">Pending</span>;
  };

  return (
    <div className="ps-page-wrapper">
      <div className="ps-gradient-bg" />

      <div className="ps-content">
        <header className="ps-header">
          <h1>Payment Status Dashboard</h1>
          <p>Lookup a payment by UUID and see its status and latest transaction.</p>
        </header>

        {/* Search Card */}
        <section className="ps-card ps-search-card">
          <label className="ps-label">Payment UUID</label>
          <div className="ps-search-row">
            <input
              type="text"
              value={uuid}
              onChange={(e) => setUuid(e.target.value)}
              placeholder="e.g. 3a67c3f4-39a2-4c02-b7b9-1L2-checkout"
            />
            <button onClick={handleSearch} disabled={loading}>
              {loading ? "Checking..." : "Check Status"}
            </button>
          </div>
          {error && <div className="ps-alert ps-alert-error">{error}</div>}
          {data && data.success && (
            <div className="ps-alert ps-alert-success">
              {data.message || "Payment status fetched successfully"}
            </div>
          )}
        </section>

        {/* Result */}
        {data && (
          <div className="ps-grid">
            {/* Payment Overview */}
            <section className="ps-card">
              <div className="ps-card-header">
                <div>
                  <h2>Payment Overview</h2>
                  <div className="ps-uuid">
                    UUID:{" "}
                    <span className="ps-mono">
                      {data.paymentUuid || "-"}
                    </span>
                  </div>
                </div>
                <div className="ps-amount-status">
                  {statusBadgePayment(data.status)}
                  {data.amount != null && (
                    <div className="ps-amount-pill">
                      {(data.amount / 100).toFixed(2)}{" "}
                      {data.currency?.toUpperCase() || ""}
                    </div>
                  )}
                </div>
              </div>

              <table className="ps-table">
                <tbody>
                  <tr>
                    <th>Description</th>
                    <td>{data.productDesc || "-"}</td>
                  </tr>
                  <tr>
                    <th>Amount</th>
                    <td>
                      {data.amount != null
                        ? `${(data.amount / 100).toFixed(2)} ${
                            data.currency?.toUpperCase() || ""
                          }`
                        : "-"}
                    </td>
                  </tr>
                  <tr>
                    <th>Status</th>
                    <td>{statusBadgePayment(data.status)}</td>
                  </tr>
                  <tr>
                    <th>Created At</th>
                    <td>
                      {data.paymentCreatedAt
                        ? new Date(data.paymentCreatedAt).toLocaleString()
                        : "-"}
                    </td>
                  </tr>
                  <tr>
                    <th>Message</th>
                    <td>{data.message || "-"}</td>
                  </tr>
                  {data.checkoutUrl && (
                    <tr>
                      <th>Checkout URL</th>
                      <td className="ps-break">
                        <a
                          href={data.checkoutUrl}
                          target="_blank"
                          rel="noreferrer"
                        >
                          {data.checkoutUrl}
                        </a>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </section>

            {/* Latest Transaction */}
            <section className="ps-card">
              <div className="ps-card-header">
                <div>
                  <h2>Latest Transaction</h2>
                  <p className="ps-subtitle">
                    Most recent gateway transaction for this payment.
                  </p>
                </div>
                {data.lastTransactionStatus && statusBadgeTransaction(data.lastTransactionStatus)}
              </div>

              {data.lastTransactionUuid ? (
                <table className="ps-table">
                  <tbody>
                    <tr>
                      <th>Transaction UUID</th>
                      <td className="ps-mono">
                        {data.lastTransactionUuid}
                      </td>
                    </tr>
                    <tr>
                      <th>Status</th>
                      <td>{statusBadgeTransaction(data.lastTransactionStatus)}</td>
                    </tr>
                    <tr>
                      <th>Gateway</th>
                      <td>{data.lastGateway || "-"}</td>
                    </tr>
                    <tr>
                      <th>Gateway Transaction ID</th>
                      <td className="ps-break ps-mono">
                        {data.lastGatewayTransactionId || "-"}
                      </td>
                    </tr>
                    <tr>
                      <th>Created At</th>
                      <td>
                        {data.lastTransactionCreatedAt
                          ? new Date(
                              data.lastTransactionCreatedAt
                            ).toLocaleString()
                          : "-"}
                      </td>
                    </tr>
                  </tbody>
                </table>
              ) : (
                <div className="ps-empty">
                  No transaction recorded for this payment yet.
                </div>
              )}
            </section>
          </div>
        )}

        {!data && !loading && !error && (
          <div className="ps-hint">
            After creating a payment in your backend, copy its{" "}
            <code>uuid</code> from the DB and paste it above to view the
            status and transaction.
          </div>
        )}
      </div>
    </div>
  );
}
