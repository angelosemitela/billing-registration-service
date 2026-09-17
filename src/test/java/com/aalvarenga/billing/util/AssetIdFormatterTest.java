package com.aalvarenga.billing.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link AssetIdFormatter}: utilitário puro (sem dependências, sem
 * estado), então cada método vira um teste trivial de "entrada esperada ->
 * saída esperada". É um dos alvos mais baratos de testar do projeto - baixo
 * esforço, alto retorno de cobertura (ver README/documentação do projeto,
 * seção de cobertura de código com JaCoCo).
 */
class AssetIdFormatterTest {

    @Test
    void formatsAccountId() {
        assertThat(AssetIdFormatter.account(1L)).isEqualTo("ACCT_1");
    }

    @Test
    void formatsProductId() {
        assertThat(AssetIdFormatter.product(2L)).isEqualTo("PROD_2");
    }

    @Test
    void formatsBillingId() {
        assertThat(AssetIdFormatter.billing(3L)).isEqualTo("BILL_3");
    }

    @Test
    void formatsPaymentIdFromLong() {
        assertThat(AssetIdFormatter.payment(4L)).isEqualTo("PAY_4");
    }

    @Test
    void formatsPaymentIdFromString() {
        // Sobrecarga usada quando o ID já chega como String (ex:
        // T_PRODUCT.DEFAULT_PAYMENT_ID) - ver javadoc da classe.
        assertThat(AssetIdFormatter.payment("4")).isEqualTo("PAY_4");
    }

    @Test
    void returnsNullWhenTechnicalIdIsNull_longOverload() {
        assertThat(AssetIdFormatter.account((Long) null)).isNull();
        assertThat(AssetIdFormatter.product((Long) null)).isNull();
        assertThat(AssetIdFormatter.billing((Long) null)).isNull();
        assertThat(AssetIdFormatter.payment((Long) null)).isNull();
    }

    @Test
    void returnsNullWhenTechnicalIdIsNull_stringOverload() {
        assertThat(AssetIdFormatter.payment((String) null)).isNull();
    }
}
