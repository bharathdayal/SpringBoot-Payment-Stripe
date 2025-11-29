// src/PaymentStatusPage.jsx
import { useState, useEffect } from "react";
import { fetchPaymentStatus, fetchPaymentList } from "./api";
import "./PaymentStatusPage.css";

export default function PaymentStatusPage() {
  const [uuid, setUuid] = useState("");
  const [data, setData] = useState(null);
  const [loadingStatus, setLoadingStatus] = useState(false);
  const [error, setError] = useState("");

  // For dropdown list
  const [payments, setPayments] = useState([]);
  const [loadingList, setLoadingList] = useState(false);
  const [listError, setListError] = useState("");

  // Load payments for dropdown on mount
  useEffect(() => {
    const loadPayments = async () => {
      setLoadingList(true);
      setListError("");
      try {
        const res = await fetchPaymentList();
        setPayments(res || []);
      } catch (e) {
        console.error(e);
        setListError("Failed to load payment list");
      } finally {
        setLoadingList(false);
      }
    };

    loadPayments();
  }, []);

  // Helper to format amount + date
  const formatAmount = (amount, currency) => {
    if (amount == null) return "-";
    return `${(amount / 100).toFixed(2)} ${currency ? currency.toUpperCase() : ""}`;
  };

  const formatDate = (iso) => (iso ? new Date(iso).toLocaleString() : "-");

  const loadStatus = async (value) => {
    const id = (value ?? uuid).trim();
    if (!id) {
      setError("Please enter or select a Payment UUID");
      return;
    }
    setError("");
    setLoadingStatus(true);
    setData(null);

    try {
      const res = await fetchPaymentStatus(id);
      setData(res);
      if (!res.success) {
        setError(res.message || "Payment not found");
      }
    } catch (e) {
      console.error(e);
      setError("Failed to fetch payment status");
    } finally {
      setLoadingStatus(false);
    }
  };

  const handleSearch = () => {
    loadStatus();
  };

  const handleSelectChange = (e) => {
    const value = e.target.value;
    setUuid(value);
    if (value) {
      loadStatus(value);
    }
  };

  const statusBadgePayment = (status) => {
    if (!status) return <span className="ps-badge ps-badge-neutral">-</span>;
    const s = status.toUpperCase();
    if (s === "CHECKOUT_CREATED") {
      return <span className="ps-badge ps-badge-success">Checkout Pending</span>;
    }
    if (s === "PAYMENT_SUCCEEDED") {
      return <span className="ps-badge ps-badge-success">Succeeded</span>;
    }
    return <span className="ps-badge ps-badge-warning">Pending</span>;
  };

  const statusBadgeTransaction = (status) => {
    if (!status) return <span className="ps-badge ps-badge-neutral">-</span>;
    const s = status.toUpperCase();
    if (s === "SUCCEEDED") {
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
          <p>
            Select a payment from the list or search by UUID to see its status and latest
            transaction.
          </p>
        </header>

        {/* Search + Dropdown Card */}
        <section className="ps-card ps-search-card">
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {/* Dropdown */}
            <div>
              <div className="ps-label">Select Payment (UUID)</div>
              <select
                value={uuid}
                onChange={handleSelectChange}
                className="ps-select"
              >
                <option value="">
                  {loadingList
                    ? "Loading payments..."
                    : "Choose a payment from the list"}
                </option>
                {payments.map((p) => (
                  <option key={p.uuid} value={p.uuid}>
                    {formatAmount(p.amount, p.currency)} · {p.status} ·{" "}
                    {formatDate(p.createdAt)} · {p.uuid}
                  </option>
                ))}
              </select>
              {listError && (
                <div className="ps-alert ps-alert-error" style={{ marginTop: 6 }}>
                  {listError}
                </div>
              )}
            </div>

            {/* Manual UUID search */}
            <div>
              <label className="ps-label">Search by UUID</label>
              <div className="ps-search-row">
                <input
                  type="text"
                  value={uuid}
                  onChange={(e) => setUuid(e.target.value)}
                  placeholder="e.g. 3a67c3f4-39a2-4c02-b7b9-1L2-checkout"
                />
                <button onClick={handleSearch} disabled={loadingStatus}>
                  {loadingStatus ? "Checking..." : "Check Status"}
                </button>
              </div>
            </div>
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
                    <span className="ps-mono">{data.paymentUuid || "-"}</span>
                  </div>
                </div>
                <div className="ps-amount-status">
                  {statusBadgePayment(data.status)}
                  {data.amount != null && (
                    <div className="ps-amount-pill">
                      {formatAmount(data.amount, data.currency)}
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
                    <td>{formatAmount(data.amount, data.currency)}</td>
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
                {data.lastTransactionStatus &&
                  statusBadgeTransaction(data.lastTransactionStatus)}
              </div>

              {data.lastTransactionUuid ? (
                <table className="ps-table">
                  <tbody>
                    <tr>
                      <th>Transaction UUID</th>
                      <td className="ps-mono">{data.lastTransactionUuid}</td>
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
                        {data.lastTransactionTransactionId || data.lastGatewayTransactionId || "-"}
                      </td>
                    </tr>
                    <tr>
                      <th>Created At</th>
                      <td>
                        {data.lastTransactionCreatedAt
                          ? new Date(data.lastTransactionCreatedAt).toLocaleString()
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

        {!data && !loadingStatus && !error && (
          <div className="ps-hint">
            After creating a payment in your backend, select it from the dropdown or
            paste its <code>uuid</code> above to view the status and transaction.
          </div>
        )}
      </div>
    </div>
  );
}
