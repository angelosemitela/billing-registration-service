package com.aalvarenga.billing.enums;

/**
 * Bandeira do cartão informado em {@code payment.brand}, obrigatória apenas
 * quando {@code payment.method} é {@code CREDIT} ou {@code DEBIT} (ver
 * {@link PaymentMethod#isCardBased()}).
 *
 * <p>Assim como {@code payment.method}/{@code product.type} já faziam, um
 * valor de entrada fora deste enum (ex: {@code "brand": "DINERS"}) é
 * rejeitado automaticamente pelo Jackson durante a desserialização do JSON,
 * antes mesmo do controller ser chamado - ver
 * {@code GlobalExceptionHandler.handleMalformedJson}. Não é necessária
 * nenhuma validação manual adicional de "valor pertence ao domínio", só a
 * checagem de OBRIGATORIEDADE (ver
 * {@code PurchaseValidationService.validatePayments}).
 */
public enum CardBrand {
    VISA,
    MASTERCARD,
    AMEX,
    ELO
}
