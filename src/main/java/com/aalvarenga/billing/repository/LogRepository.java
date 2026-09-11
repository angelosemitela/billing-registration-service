package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.LogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LogRepository extends JpaRepository<LogEntity, Long> {

    List<LogEntity> findByProtocol(String protocol);
}
