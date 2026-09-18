package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DomainDiscountStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainDiscountStatusRepository extends JpaRepository<DomainDiscountStatusEntity, Integer> {
}
