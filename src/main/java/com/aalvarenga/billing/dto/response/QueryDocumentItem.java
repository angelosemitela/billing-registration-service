package com.aalvarenga.billing.dto.response;

/**
 * Item da lista {@code account[].document} da consulta de dados.
 *
 * @param value valor do documento já MASCARADO (ver
 *              {@code com.aalvarenga.billing.util.MaskingUtil}) - nunca o
 *              valor bruto gravado em {@code T_ACCOUNT_DOCUMENT.VALUE}.
 */
public record QueryDocumentItem(
        String type,
        String description,
        String value
) {
}
