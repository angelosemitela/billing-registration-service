package com.aalvarenga.billing.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link MoneyUtil} - utilitário puro (sem dependências), central para
 * TODA comparação de valores monetários do projeto (usado por
 * {@code BillingService} e {@code PurchaseValidationService}).
 *
 * <p>O caso mais importante aqui é {@link MoneyUtil#equalsMoney}: prova que
 * {@code 10.5} e {@code 10.50} são tratados como o MESMO valor (diferença de
 * escala, não de valor) - exatamente o problema que a classe existe para
 * evitar (ver javadoc de {@link MoneyUtil}, que explica por que
 * {@code BigDecimal.equals()} sozinho não serve para dinheiro).
 */
class MoneyUtilTest {

    @Test
    void equalsMoney_sameValueDifferentScale_isEqual() {
        // 10.5 (escala 1) e 10.50 (escala 2) são o MESMO valor monetário -
        // BigDecimal.equals() os consideraria diferentes, mas equalsMoney não.
        assertThat(MoneyUtil.equalsMoney(new BigDecimal("10.5"), new BigDecimal("10.50"))).isTrue();
    }

    @Test
    void equalsMoney_differentValues_isNotEqual() {
        assertThat(MoneyUtil.equalsMoney(new BigDecimal("10.00"), new BigDecimal("10.01"))).isFalse();
    }

    @Test
    void equalsMoney_bothNull_isEqual() {
        // a == b quando os dois são null (null == null é true em Java).
        assertThat(MoneyUtil.equalsMoney(null, null)).isTrue();
    }

    @Test
    void equalsMoney_onlyOneNull_isNotEqual() {
        assertThat(MoneyUtil.equalsMoney(null, BigDecimal.ZERO)).isFalse();
        assertThat(MoneyUtil.equalsMoney(BigDecimal.ZERO, null)).isFalse();
    }

    @Test
    void equalsMoney_appliesHalfUpRoundingBeforeComparing() {
        // 10.005 arredonda (HALF_UP) para 10.01 em 2 casas - deve bater com "10.01" já normalizado.
        assertThat(MoneyUtil.equalsMoney(new BigDecimal("10.005"), new BigDecimal("10.01"))).isTrue();
    }

    @Test
    void isNegative_negativeValue_isTrue() {
        assertThat(MoneyUtil.isNegative(new BigDecimal("-0.01"))).isTrue();
    }

    @Test
    void isNegative_zeroOrPositive_isFalse() {
        assertThat(MoneyUtil.isNegative(BigDecimal.ZERO)).isFalse();
        assertThat(MoneyUtil.isNegative(new BigDecimal("0.01"))).isFalse();
    }

    @Test
    void isNegative_null_isFalse() {
        // Fail-safe: null não é tratado como "negativo" (evita NPE em quem chama).
        assertThat(MoneyUtil.isNegative(null)).isFalse();
    }

    @Test
    void isZero_exactlyZero_isTrue() {
        assertThat(MoneyUtil.isZero(BigDecimal.ZERO)).isTrue();
        // "0.00" também é zero, mesmo com escala diferente de BigDecimal.ZERO (compareTo, não equals).
        assertThat(MoneyUtil.isZero(new BigDecimal("0.00"))).isTrue();
    }

    @Test
    void isZero_nonZero_isFalse() {
        assertThat(MoneyUtil.isZero(new BigDecimal("0.01"))).isFalse();
    }

    @Test
    void isZero_null_isFalse() {
        assertThat(MoneyUtil.isZero(null)).isFalse();
    }
}
