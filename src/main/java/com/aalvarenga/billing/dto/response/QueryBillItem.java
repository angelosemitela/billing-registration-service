package com.aalvarenga.billing.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Item da lista {@code bill} da consulta de dados
 * ({@code POST /api/v1/purchases/query}).
 *
 * @param billId     ID técnico formatado (prefixo {@code BILL_}).
 * @param productId  ID técnico formatado (prefixo {@code PROD_}) do produto
 *                   ao qual esta fatura pertence.
 * @param splitValue valor de UMA parcela, usando divisão INTEIRA (sem
 *                   sobra) de {@code chargedValue} por {@code installments},
 *                   arredondado para 2 casas decimais - ver
 *                   {@code PurchaseQueryService} para a decisão tomada
 *                   diante de uma inconsistência do anexo original (que
 *                   descreve a fórmula como "productValue / installments",
 *                   mas cujo próprio exemplo numérico só bate usando
 *                   {@code chargedValue}).
 */
public record QueryBillItem(
        String billId,
        String createdDt,
        String cycleStartDt,
        String cycleEndDt,
        String dueDt,
        String productId,
        BigDecimal productValue,
        BigDecimal discountValue,
        BigDecimal taxValue,
        BigDecimal chargedValue,
        Integer installments,
        BigDecimal splitValue,
        String currency,
        String provider,
        String paymentMethod,
        String billSt,
        String billType,
        List<QueryTaxItem> tax
) {
}
