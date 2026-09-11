package com.aalvarenga.billing.dto.response;

/**
 * Item da lista {@code product} da resposta de sucesso.
 *
 * <p>Nota: o enunciado grafa o campo de retorno como {@code "codeid"}
 * (com "d" minúsculo), inconsistente com o {@code "codeId"} da entrada.
 * Padronizamos para {@code codeId} (camelCase) na resposta - ver
 * README/ANALISE.md, seção "Inconsistências".
 */
public record ProductResponseItem(
        String codeId,
        String id,
        String nextBillDate,
        String cycleEndDate,
        Boolean isTrial,
        String paymentAssetId
) {
}
