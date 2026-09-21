package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DomainProductCancellationStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainProductCancellationStatusRepository extends JpaRepository<DomainProductCancellationStatusEntity, Integer> {
}
