package com.aalvarenga.billing.dto.request;

import java.math.BigDecimal;

/** Estrutura {@code billing.tax} da entrada. */
public record TaxRequest(
        String name,
        BigDecimal value
) {
}
