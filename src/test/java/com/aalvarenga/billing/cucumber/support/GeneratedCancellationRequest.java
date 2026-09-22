package com.aalvarenga.billing.cucumber.support;

/**
 * Resultado de {@link CancellationRequestJsonBuilder#buildBaseRequestJson}.
 *
 * <p>Mesmo racional de {@link GeneratedPurchaseRequest}: devolve, junto do
 * JSON pronto para uso, os identificadores que os passos seguintes do
 * cenário (ou o próprio relatório de falha de uma asserção) podem precisar
 * sem ter que reabrir o JSON e reextrair na mão.
 *
 * @param json       corpo da requisição de cancelamento, já com os
 *                   placeholders substituídos por valores únicos/recuperados
 *                   da compra que antecede o cancelamento (ver
 *                   {@link CancellationRequestJsonBuilder})
 * @param protocolId {@code protocolId} único gerado para este cenário
 * @param productId  {@code productId} recuperado da resposta da compra
 *                   ({@code product[0].id}) e usado como entrada deste
 *                   cancelamento
 */
public record GeneratedCancellationRequest(String json, String protocolId, String productId) {
}
