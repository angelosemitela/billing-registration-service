package com.aalvarenga.billing.cucumber.support;

/**
 * Resultado de {@link PurchaseRequestJsonBuilder#buildBaseRequestJson()}:
 * carrega tanto o JSON pronto para ser enviado (já com identificadores
 * únicos) quanto os próprios identificadores gerados, separadamente - assim
 * as classes de step definitions não precisam reabrir/reanalisar o JSON só
 * para descobrir, por exemplo, qual {@code externalId} foi usado, na hora de
 * consultar o banco de dados diretamente (ver
 * {@code PurchaseApiSteps.entaoAContaDeveExistirNaBaseComOExternalIdInformado}).
 *
 * @param json         corpo pronto da requisição (JSON), com todos os
 *                     placeholders da fixture já substituídos
 * @param protocol     protocolo único gerado para este cenário
 * @param externalId   externalId único gerado para este cenário
 */
public record GeneratedPurchaseRequest(String json, String protocol, String externalId) {
}
