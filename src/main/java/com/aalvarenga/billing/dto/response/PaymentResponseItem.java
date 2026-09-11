package com.aalvarenga.billing.dto.response;

/** Item da lista {@code payment} da resposta de sucesso. */
public record PaymentResponseItem(
        String method,
        Boolean isDefault,
        String id
) {
}
