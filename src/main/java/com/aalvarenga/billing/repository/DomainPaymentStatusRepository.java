package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DomainPaymentStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainPaymentStatusRepository extends JpaRepository<DomainPaymentStatusEntity, Integer> {
}
