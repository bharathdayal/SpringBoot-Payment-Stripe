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
