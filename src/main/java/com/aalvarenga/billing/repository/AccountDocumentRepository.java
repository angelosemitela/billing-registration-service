package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.AccountDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountDocumentRepository extends JpaRepository<AccountDocumentEntity, Long> {

    Optional<AccountDocumentEntity> findByAccountIdAndType(Long accountId, String type);
}
