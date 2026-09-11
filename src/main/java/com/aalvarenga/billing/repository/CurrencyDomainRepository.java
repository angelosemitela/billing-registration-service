package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.CurrencyDomainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrencyDomainRepository extends JpaRepository<CurrencyDomainEntity, String> {
}
