package com.financialtransaction.transfer.repository;

import com.financialtransaction.transfer.entity.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer,Long> {
    Optional<Transfer> findByTransactionId(String transactionId);
}
