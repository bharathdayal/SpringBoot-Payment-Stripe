package com.example.payment_process.service;

import com.example.payment_process.dto.OrderRequest;
import com.example.payment_process.dto.PaymentResponse;
import com.example.payment_process.repository.PaymentRepository;
import org.aspectj.weaver.ast.Or;
import org.springframework.core.annotation.Order;

public interface PaymentService {
    PaymentResponse createPayment(OrderRequest request,String idempotencyKey);
    String createCheckoutSession(OrderRequest request,String idempotencyKey);
}
