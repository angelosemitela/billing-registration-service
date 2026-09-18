package com.aalvarenga.billing.dto.response;

import java.math.BigDecimal;

/** Item da lista {@code bill[].tax} da consulta de dados. */
public record QueryTaxItem(
        String name,
        BigDecimal value
) {
}
