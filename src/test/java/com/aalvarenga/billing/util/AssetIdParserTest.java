package com.aalvarenga.billing.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link AssetIdParser}: a operação inversa de {@link AssetIdFormatter}
 * (formata "PROD_1" &lt;- 1L; aqui, 1L &lt;- "PROD_1"). Assim como
 * {@code AssetIdFormatterTest}, é um utilitário puro - cada caso vira um
 * teste trivial de entrada/saída, sem necessidade de mocks.
 *
 * <p>Extraído durante a feature de cancelamento (ver decisoes.md) a partir de
 * uma lógica que antes vivia, duplicada, dentro de
 * {@link com.aalvarenga.billing.service.PurchaseQueryValidationService} -
 * este teste também serve de rede de segurança para aquele refactor.
 */
class AssetIdParserTest {

    @Test
    void parsesProductIdWithPrefix() {
        assertThat(AssetIdParser.parseProduct("PROD_123")).isEqualTo(123L);
    }

    @Test
    void parsesProductIdWithoutPrefix() {
        // Aceitar o ID técnico "cru" (sem prefixo) é intencional - ver
        // javadoc de AssetIdParser e de CancellationRequest.productId().
        assertThat(AssetIdParser.parseProduct("123")).isEqualTo(123L);
    }

    @Test
    void parsesBillingIdWithPrefix() {
        assertThat(AssetIdParser.parseBilling("BILL_9")).isEqualTo(9L);
    }

    @Test
    void parsesBillingIdWithoutPrefix() {
        assertThat(AssetIdParser.parseBilling("9")).isEqualTo(9L);
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(AssetIdParser.parseProduct(null)).isNull();
        assertThat(AssetIdParser.parseBilling(null)).isNull();
    }

    @Test
    void returnsNullForNonNumericGarbage() {
        // Erro de negócio (404/400) é decisão de quem CHAMA o parser - ver
        // javadoc da classe - aqui só interessa que o retorno é null.
        assertThat(AssetIdParser.parseProduct("PROD_abc")).isNull();
        assertThat(AssetIdParser.parseBilling("not-a-number")).isNull();
    }

    @Test
    void returnsNullWhenPrefixDoesNotMatchTheExpectedAssetType() {
        // "BILL_9" pedido como produto: o prefixo errado não é removido,
        // então a tentativa de Long.parseLong("BILL_9") falha -> null.
        assertThat(AssetIdParser.parseProduct("BILL_9")).isNull();
    }
}
