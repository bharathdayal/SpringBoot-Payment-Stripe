package com.example.payment_process.repository;

import com.example.payment_process.model.Payment;
import com.example.payment_process.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction,Long> {

    Optional<Transaction> findByUuid(String uuid);
    Optional<Transaction> findByGatewayTransactionId(String gatewayTransactionId);
    List<Transaction> findByPaymentOrderByCreatedAtDesc(Payment payment);
}
