package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.BillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillRepository extends JpaRepository<BillEntity, Long> {

    boolean existsByTransactionId(String transactionId);

    /**
     * Faturas de um conjunto de produtos, já ordenadas de forma decrescente
     * por {@code DUE_DT} - regra explícita do anexo da consulta de dados
     * ("considerar sempre os registros em modo decrescente em relação ao
     * campo T_BILL:DUE_DT"). Deixar o banco já devolver ordenado evita ter
     * que reordenar a lista inteira em memória do lado da aplicação.
     */
    List<BillEntity> findByProductIdInOrderByDueDtDesc(List<Long> productIds);
}
