package com.aalvarenga.billing.dto.response;

import java.math.BigDecimal;

/** Item da lista {@code billing} da resposta de sucesso. */
public record BillingResponseItem(
        String codeId,
        String product,
        String paymentMethod,
        BigDecimal chargedValue,
        String currency,
        String id
) {
}
