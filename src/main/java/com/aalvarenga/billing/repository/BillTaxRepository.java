package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.BillTaxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillTaxRepository extends JpaRepository<BillTaxEntity, Long> {

    /** Taxas de um conjunto de faturas - usado pela consulta de dados para montar {@code bill[].tax}. */
    List<BillTaxEntity> findByBillIdIn(List<Long> billIds);
}
