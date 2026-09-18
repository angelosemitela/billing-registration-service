package com.aalvarenga.billing.dto.response;

/**
 * Item da lista {@code payment} da consulta de dados
 * ({@code POST /api/v1/purchases/query}).
 *
 * @param paymentId  ID técnico formatado (prefixo {@code PAY_}).
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
        String issuer,
        String cardNumber,
        String expiration,
        Boolean isMultiple,
        Boolean isDefault,
        Integer installments,
        String paymentSt
) {
}
