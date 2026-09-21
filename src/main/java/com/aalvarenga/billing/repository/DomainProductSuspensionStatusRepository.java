package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DomainProductSuspensionStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainProductSuspensionStatusRepository extends JpaRepository<DomainProductSuspensionStatusEntity, Integer> {
}
