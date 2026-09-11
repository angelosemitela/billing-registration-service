package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.AccountAddressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAddressRepository extends JpaRepository<AccountAddressEntity, Long> {
}
