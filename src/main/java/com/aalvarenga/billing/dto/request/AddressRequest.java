package com.aalvarenga.billing.dto.request;

import com.aalvarenga.billing.enums.AddressType;

/** Estrutura {@code account.address} da entrada. */
public record AddressRequest(
        AddressType type,
        String description,
        String addressName,
        String number,
        String complement,
        String zipCode,
        String country
) {
}
