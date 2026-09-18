package com.aalvarenga.billing.dto.response;

import java.util.List;

/**
 * Item da lista {@code account} da consulta de dados
 * ({@code POST /api/v1/purchases/query}).
 *
 * @param accountId  ID técnico formatado (prefixo {@code ACCT_}, ver
 *                   {@code com.aalvarenga.billing.util.AssetIdFormatter}).
 * @param document   documentos com {@code STATUS = 1}; {@code null} se não
 *                   houver nenhum elegível (regra explícita do anexo).
 * @param phone      telefones com {@code STATUS = 1}; {@code null} se não
 *                   houver nenhum elegível (mesma regra).
 */
public record QueryAccountItem(
        String externalId,
        String accountId,
        String createdDt,
        String email,
        String accountSt,
        Boolean isAuthorizedFallback,
        List<QueryDocumentItem> document,
        List<QueryPhoneItem> phone
) {
}
