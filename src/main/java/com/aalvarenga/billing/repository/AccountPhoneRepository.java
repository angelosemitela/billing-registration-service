package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.AccountPhoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountPhoneRepository extends JpaRepository<AccountPhoneEntity, Long> {

    /** Telefones ativos de uma conta - usado pela consulta de dados (STATUS = 1, ver anexo). */
    List<AccountPhoneEntity> findByAccountIdAndStatus(Long accountId, Integer status);
}
