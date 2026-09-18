package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    /**
     * Meios de pagamento ativos de uma conta - usado pela consulta de dados
     * quando o filtro é {@code externalId} (regra explícita do anexo:
     * "retorna os registros que estejam ativos T_PAYMENT:STATUS igual a 1").
     */
    List<PaymentEntity> findByAccountIdAndStatus(Long accountId, Integer status);
}
