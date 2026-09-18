package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DomainAccountStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainAccountStatusRepository extends JpaRepository<DomainAccountStatusEntity, Integer> {
}
