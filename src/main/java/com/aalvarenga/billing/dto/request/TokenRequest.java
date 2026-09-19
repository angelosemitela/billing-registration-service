package com.aalvarenga.billing.dto.request;

/**
 * Estrutura {@code payment.token} da entrada.
 *
 * @param name         identificação do token (ex: "ACCESS_TOKEN")
 * @param id           valor do token gerado pelo gateway
 * @param gateway      gateway que gerou o token
 * @param expirationDt data de expiração em epoch milissegundos (String), opcional -
 *                     campo renomeado de {@code expirationDate} para
 *                     {@code expirationDt} em 19/09/2026, para seguir o mesmo
 *                     padrão de nomenclatura dos demais campos de data/hora da
 *                     API (ex: {@code transactionDt}). Quando informado, NÃO
 *                     pode representar uma data no passado - ver
 *                     {@link com.aalvarenga.billing.service.PurchaseValidationService#validate}
 *                     (regra funcional: um token/método de pagamento não pode
 *                     entrar na base já expirado).
 */
public record TokenRequest(
        String name,
        String id,
        String gateway,
        String expirationDt
) {
}
