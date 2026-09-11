package com.aalvarenga.billing.enums;

/**
 * Periodicidade de recobrança: {@code product.recurrenceFrequency} da entrada.
 * Obrigatório apenas quando {@code product.type == RECURRENCE}.
 */
public enum RecurrenceFrequency {
    MONTH,
    ANNUAL
}
