package com.aalvarenga.billing.dto.response;

import java.math.BigDecimal;

/**
 * Item da lista {@code products[].discount} da consulta de dados.
 *
 * <p>Só entram nesta lista descontos "vigentes" ({@code STATUS = 1} E
 * {@code END_DT} ainda no futuro) - ver
 * {@code com.aalvarenga.billing.service.PurchaseQueryService} para a
 * decisão tomada diante da contradição encontrada no anexo original entre
 * esta regra e a de {@code nextBillValue} (documentada no doc de decisões
 * do projeto).
 *
 * @param discountId ID técnico formatado (prefixo {@code DISC_}, ver
 *                   {@code com.aalvarenga.billing.util.AssetIdFormatter}).
 */
public record QueryDiscountItem(
        String discountId,
        String createdDt,
        String startDt,
        String endDt,
        BigDecimal value,
        String discountSt
) {
}
