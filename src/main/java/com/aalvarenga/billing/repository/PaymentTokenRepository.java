package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.PaymentTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentTokenRepository extends JpaRepository<PaymentTokenEntity, Long> {

    boolean existsByToken(String token);
}
