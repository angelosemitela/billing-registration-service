package com.aalvarenga.billing.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Item da lista {@code products} da consulta de dados
 * ({@code POST /api/v1/purchases/query}).
 *
 * @param productId       ID técnico formatado (prefixo {@code PROD_}).
 * @param externalProductId {@code T_PRODUCT.PRODUCT_ID} - o {@code codeId}
 *                        informado na compra original (ver javadoc de
 *                        {@code com.aalvarenga.billing.entity.ProductEntity#getProductId()}).
 * @param isActiveTrial   {@code true} quando o produto está em trial E
 *                        ainda dentro do período de cortesia - calculado
 *                        via {@code RecurrenceCalculatorService.calculateTrialEndDate}
 *                        (mesma regra já usada para calcular a próxima
 *                        cobrança pós-trial na criação da compra).
 * @param defaultPaymentId ID técnico formatado (prefixo {@code PAY_}) do
 *                        pagamento padrão do produto; {@code null} se o
 *                        produto não tiver um definido.
 * @param nextBillValue   valor da próxima cobrança já descontando os
 *                        descontos vigentes (ver {@code discount}); igual a
 *                        {@code value} quando não há desconto vigente.
 * @param discount        descontos vigentes deste produto; {@code null} se
 *                        não houver nenhum (ver {@link QueryDiscountItem}).
 */
public record QueryProductItem(
        String productId,
        String channel,
        String externalProductId,
        String name,
        String assetId,
        String createdDt,
        String transactionDt,
        String type,
        Boolean isExpirationService,
        Boolean isTrial,
        Integer trialDays,
        String cycleStartDt,
        String cycleEndDt,
        String nextBillDt,
        String recurrenceFrequency,
        String currency,
        BigDecimal value,
        Boolean isActiveTrial,
        String defaultPaymentId,
        String productSt,
        String cancellationReqDt,
        String cancellationSchDt,
        BigDecimal nextBillValue,
        List<QueryDiscountItem> discount
) {
}
