package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositório Spring Data JPA para {@link AccountEntity}.
 *
 * <p>Note que não escrevemos nenhuma implementação: o Spring Data JPA gera a
 * implementação em tempo de execução a partir da assinatura do método
 * (padrão "Query Method") - por exemplo, {@code findByExternalId} vira
 * automaticamente {@code SELECT * FROM T_ACCOUNT WHERE EXTERNAL_ID = ?}.
 */
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    Optional<AccountEntity> findByExternalId(String externalId);
}
