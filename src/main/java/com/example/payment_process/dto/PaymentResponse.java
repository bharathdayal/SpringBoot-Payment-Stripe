package com.example.payment_process.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private boolean success;
    private String paymentId;
    private String clientSecret;
    private String  message;
    private Long orderId;
    private String productDesc;
    private Long amount;
    private String currency;
    private String status;
    private String paymentUuid;

    // latest transaction info
    private String lastTransactionUuid;
    private String lastTransactionStatus;
    private String lastGateway;
    private String lastGatewayTransactionId;
    private Instant lastTransactionCreatedAt;

}
