package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.ConfigFeatureToggleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Repositório Spring Data JPA para {@link ConfigFeatureToggleEntity}. */
public interface ConfigFeatureToggleRepository extends JpaRepository<ConfigFeatureToggleEntity, Long> {

    Optional<ConfigFeatureToggleEntity> findByRuleName(String ruleName);
}
