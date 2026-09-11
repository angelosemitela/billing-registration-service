package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.BillInstallmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillInstallmentRepository extends JpaRepository<BillInstallmentEntity, Long> {
}
