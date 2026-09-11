package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.CountryDomainEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryDomainRepository extends JpaRepository<CountryDomainEntity, String> {
}
