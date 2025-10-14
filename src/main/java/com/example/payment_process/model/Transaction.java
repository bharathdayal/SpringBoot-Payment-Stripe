package com.example.payment_process.model;

import jakarta.persistence.*;
import jdk.jfr.DataAmount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="payment_id")
    private Payment payment;

    @Column(unique = true,nullable = false)
    private String uuid;

    private String gatewayTransactionId;
    private String gateway;
    private String status;
    private Instant createdAt;

    @PrePersist
    protected  void onCreate() {
        if(this.uuid==null)this.uuid= UUID.randomUUID().toString();
        if(this.createdAt==null)this.createdAt= Instant.now();
    }
}
