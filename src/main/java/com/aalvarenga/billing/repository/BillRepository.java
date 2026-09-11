package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.BillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillRepository extends JpaRepository<BillEntity, Long> {

    boolean existsByTransactionId(String transactionId);
}
