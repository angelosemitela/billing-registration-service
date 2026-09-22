package com.aalvarenga.billing.dto.response;

/**
 * Item da lista {@code payment} da consulta de dados
 * ({@code POST /api/v1/purchases/query}).
 *
 * @param paymentId  ID técnico formatado (prefixo {@code PAY_}).
 * @param brand      bandeira do cartão ({@code T_PAYMENT.BRAND} - só
 *                   preenchida quando {@code method} é {@code CREDIT}/
 *                   {@code DEBIT}, {@code null} para PIX/WALLET). Campo
 *                   acrescentado em 21/09/2026 a pedido do usuário: o dado
 *                   já era persistido na criação da compra (ver
 *                   {@code V8__add_account_email_fallback_and_payment_brand.sql})
 *                   mas não aparecia nesta consulta - não fazia parte do
 *                   anexo original de especificação (ver README, seção
 *                   "Evoluções pedidas em 21/09/2026").
 * @param cardNumber número do cartão já MASCARADO, mantendo os 4 últimos
 *                   dígitos visíveis (ver
 *                   {@code com.aalvarenga.billing.util.MaskingUtil} e o
 *                   doc de decisões do projeto - decisão tomada porque o
 *                   anexo original não previa máscara para este campo, mas
 *                   devolvê-lo em texto puro numa API de consulta seria uma
 *                   prática de segurança ruim).
 */
public record QueryPaymentItem(
        String paymentId,
        String createdDt,
        String method,
        String brand,
        String issuer,
        String cardNumber,
        String expiration,
        Boolean isMultiple,
        Boolean isDefault,
        Integer installments,
        String paymentSt
) {
}
