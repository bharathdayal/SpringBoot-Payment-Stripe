package com.example.payment_process.model;

import jakarta.persistence.*;
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
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true,nullable = false)
    private String uuid;

    private Long amount;
    private String currency;
    private String description;
    private String status;
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if(this.uuid==null)this.uuid= UUID.randomUUID().toString();
        if(this.createdAt==null)this.createdAt=Instant.now();
    }

    @Column(name="checkout_url",length = 2000)
    private String checkoutUrl;
}
