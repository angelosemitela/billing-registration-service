package com.aalvarenga.billing.repository;

import com.aalvarenga.billing.entity.DiscountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscountRepository extends JpaRepository<DiscountEntity, Long> {

    /**
     * Todos os descontos de um conjunto de produtos (sem filtro de
     * status/vigência aqui - quem decide o que é "ativo" é
     * {@code PurchaseQueryService}, já que essa regra usa o instante atual
     * e não daria pra expressar como Query Method).
     */
    List<DiscountEntity> findByProductIdIn(List<Long> productIds);
}
