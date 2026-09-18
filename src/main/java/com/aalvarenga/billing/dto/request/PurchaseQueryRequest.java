package com.aalvarenga.billing.dto.request;

/**
 * Corpo (body) da requisição de consulta de dados -
 * {@code POST /api/v1/purchases/query}.
 *
 * <p>Igual à {@link PurchaseRequest}, nenhuma anotação de Bean Validation é
 * usada aqui: a única regra estrutural do contrato ("externalId OU
 * productId, pelo menos um dos dois") é uma validação CRUZADA entre dois
 * campos, e as três flags de retorno e o {@code maxBillReturn} têm um
 * comportamento de "valor padrão" quando ausentes/nulos/inválidos que a
 * Bean Validation não expressa bem sozinha - por isso tudo fica centralizado
 * em {@link com.aalvarenga.billing.service.PurchaseQueryValidationService},
 * seguindo o mesmo racional já documentado em {@link PurchaseRequest} (ver
 * também README, seção "Por que não usar só Bean Validation").
 *
 * @param externalId        ID externo da conta (ver {@code T_ACCOUNT.EXTERNAL_ID}).
 *                          Opcional se {@code productId} for informado -
 *                          exatamente um dos dois deve estar preenchido.
 * @param productId         ID do produto retornado por uma consulta/compra
 *                          anterior (formato {@code "PROD_&lt;id&gt;"}, o
 *                          mesmo devolvido em {@code ProductResponseItem.id}
 *                          e em {@code QueryProductItem.productId} - um ID
 *                          técnico puro também é aceito, por robustez, ver
 *                          {@code PurchaseQueryService}). Opcional se
 *                          {@code externalId} for informado.
 * @param returnProductData se {@code false}, omite a estrutura
 *                          {@code products} da resposta. Qualquer outro
 *                          valor (incluindo ausente/{@code null}) é tratado
 *                          como {@code true}.
 * @param returnPaymentData mesma regra de {@code returnProductData}, para a
 *                          estrutura {@code payment}.
 * @param returnBillData    mesma regra de {@code returnProductData}, para a
 *                          estrutura {@code bill}.
 * @param maxBillReturn     número máximo de faturas devolvidas em
 *                          {@code bill} (as mais recentes primeiro, ver
 *                          {@code BillRepository.findByProductIdInOrderByDueDtDesc}).
 *                          Ausente, nulo ou não seja um inteiro válido é
 *                          tratado como {@code 0}; {@code 0} significa "sem
 *                          limite" (devolve todas).
 */
public record PurchaseQueryRequest(
        String externalId,
        String productId,
        Boolean returnProductData,
        Boolean returnPaymentData,
        Boolean returnBillData,
        Integer maxBillReturn
) {
}
