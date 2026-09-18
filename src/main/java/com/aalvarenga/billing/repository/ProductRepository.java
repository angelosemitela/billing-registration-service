package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    /**
     * Todos os produtos de uma conta, independente de {@code STATUS} -
     * usado pela consulta de dados quando o filtro é {@code externalId}
     * (regra explícita do anexo: "retornar os registros independente do
     * status da tabela").
     */
    List<ProductEntity> findByAccountId(Long accountId);
}
