package com.example.payment_process.service;

import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import com.example.payment_process.model.Payment;


public interface PaymentService {
    PaymentResponse createPayment(OrderRequest request,String idempotencyKey);
    String createCheckoutSession(OrderRequest request,String idempotencyKey);
    String handleWebhook(String sigHeader, String payload);
    PaymentResponse getPaymentStatus(String uuId);
    PaymentResponse getPaymentStatus(Long id);



}
