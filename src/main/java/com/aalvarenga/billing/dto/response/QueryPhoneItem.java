package com.aalvarenga.billing.dto.response;

/** Item da lista {@code account[].phone} da consulta de dados. */
public record QueryPhoneItem(
        String number
) {
}
