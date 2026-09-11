package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountRepository extends JpaRepository<DiscountEntity, Long> {
}
