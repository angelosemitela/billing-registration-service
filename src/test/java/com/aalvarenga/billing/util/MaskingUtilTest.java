package com.aalvarenga.billing.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link MaskingUtil}: usado pela consulta de dados para mascarar
 * documento (2 últimos dígitos visíveis) e número de cartão (4 últimos
 * dígitos visíveis) - ver {@code PurchaseQueryService}.
 */
class MaskingUtilTest {

    @Test
    void keepsOnlyLastDigitsVisible() {
        assertThat(MaskingUtil.maskKeepingLast("12345678922", 2)).isEqualTo("*********22");
    }

    @Test
    void masksEntirelyWhenValueIsShorterThanVisibleSuffix() {
        assertThat(MaskingUtil.maskKeepingLast("123", 4)).isEqualTo("***");
    }

    @Test
    void masksEntirelyWhenValueLengthEqualsVisibleSuffix() {
        // Regra explícita do anexo: valor com EXATAMENTE 4 posições -> mascarado por inteiro.
        assertThat(MaskingUtil.maskKeepingLast("1234", 4)).isEqualTo("****");
    }

    @Test
    void keepsLastFourDigitsVisibleForCardNumber() {
        assertThat(MaskingUtil.maskKeepingLast("4111111111111111", 4)).isEqualTo("************1111");
    }

    @Test
    void returnsNullWhenValueIsNull() {
        assertThat(MaskingUtil.maskKeepingLast(null, 2)).isNull();
    }

    @Test
    void returnsEmptyWhenValueIsEmpty() {
        assertThat(MaskingUtil.maskKeepingLast("", 2)).isEmpty();
    }
}
