package com.aalvarenga.billing.enums;

/** Métodos de pagamento aceitos em {@code payment.method} e {@code billing.paymentMethod}. */
public enum PaymentMethod {
    CREDIT,
    DEBIT,
    PIX,
    WALLET;

    /** Cartão de crédito, débito ou wallet de cartões admitem parcelamento (1 a 12x); PIX não. */
    public boolean allowsInstallments() {
        return this == CREDIT || this == WALLET;
    }

    /** Apenas CREDIT/DEBIT exigem dados de cartão (número, validade, isMultiple). */
    public boolean isCardBased() {
        return this == CREDIT || this == DEBIT;
    }
}
