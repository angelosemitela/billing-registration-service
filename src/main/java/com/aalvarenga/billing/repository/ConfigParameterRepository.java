package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.ConfigParameterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Repositório Spring Data JPA para {@link ConfigParameterEntity}. */
public interface ConfigParameterRepository extends JpaRepository<ConfigParameterEntity, Long> {

    Optional<ConfigParameterEntity> findByParameterName(String parameterName);
}
