package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DomainProductStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainProductStatusRepository extends JpaRepository<DomainProductStatusEntity, Integer> {
}
