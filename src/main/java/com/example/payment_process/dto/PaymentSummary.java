package com.example.payment_process.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentSummary {

    private String uuid;
    private Long amount;
    private String currency;
    private String status;
    private Instant createdAt;
    private String description;
}
