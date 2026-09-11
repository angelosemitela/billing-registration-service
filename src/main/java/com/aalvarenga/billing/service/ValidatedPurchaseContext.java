package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.AccountEntity;

/**
 * Pequeno "resultado" da validação que a orquestração de persistência
 * ({@link PurchaseOrchestrationService}) reaproveita, evitando fazer de novo
 * um trabalho que a validação já fez (parse de {@code transactionDate} e
 * busca da conta existente no banco, quando {@code account.id} foi
 * informado).
 *
 * @param transactionDateEpochMillis {@code transactionDate} já convertido e validado
 * @param existingAccount            conta já carregada do banco, se {@code account.id} foi informado; {@code null} caso contrário
 */
public record ValidatedPurchaseContext(
        long transactionDateEpochMillis,
        AccountEntity existingAccount
) {
}
