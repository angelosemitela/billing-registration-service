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

    // As duas anotações abaixo suprimem um warning do IntelliJ ("Result of
    // '...' is always 'null'") que é um FALSO POSITIVO aqui: a análise de
    // fluxo de dados da IDE percebe corretamente que, dado um argumento
    // literal null, o método SEMPRE retorna null - só que esse é
    // exatamente o comportamento que este teste existe para comprovar, não
    // um bug. Sem a anotação, a IDE mostraria warning para um teste
    // correto e intencional.
    @SuppressWarnings("ConstantValue")
    @Test
    void returnsNullWhenTechnicalIdIsNull_longOverload() {
        // Cast para Long é redundante em account/product/billing (só existe
        // uma sobrecarga cada) - a IDE também aponta isso corretamente, por
        // isso ele foi removido aqui. Já em payment(...) o cast é
        // OBRIGATÓRIO: sem ele, "null" seria ambíguo entre as sobrecargas
        // payment(Long) e payment(String) e o código não compilaria.
        assertThat(AssetIdFormatter.account(null)).isNull();
        assertThat(AssetIdFormatter.product(null)).isNull();
        assertThat(AssetIdFormatter.billing(null)).isNull();
        assertThat(AssetIdFormatter.payment((Long) null)).isNull();
    }

    @SuppressWarnings("ConstantValue")
    @Test
    void returnsNullWhenTechnicalIdIsNull_stringOverload() {
        assertThat(AssetIdFormatter.payment((String) null)).isNull();
    }
}
