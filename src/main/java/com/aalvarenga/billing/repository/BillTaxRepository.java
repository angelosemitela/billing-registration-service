package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.BillTaxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillTaxRepository extends JpaRepository<BillTaxEntity, Long> {
}
