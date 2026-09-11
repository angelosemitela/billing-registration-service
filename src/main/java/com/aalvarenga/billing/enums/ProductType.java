package com.aalvarenga.billing.enums;

/**
 * Modalidade de recobrança de um produto vendido: {@code product.type} da entrada.
 *
 * <ul>
 *   <li>{@link #ONESHOT} - venda avulsa, sem recobrança futura;</li>
 *   <li>{@link #RECURRENCE} - assinatura recorrente (mensal ou anual, ver
 *       {@link RecurrenceFrequency}), gerada automaticamente a cada ciclo.</li>
 * </ul>
 */
public enum ProductType {
    ONESHOT,
    RECURRENCE
}
