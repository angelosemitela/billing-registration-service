package com.aalvarenga.billing.dto.request;

/**
 * Estrutura {@code payment.token} da entrada.
 *
 * @param name           identificação do token (ex: "ACCESS_TOKEN")
 * @param id             valor do token gerado pelo gateway
 * @param gateway        gateway que gerou o token
 * @param expirationDate data de expiração em epoch milissegundos (String), opcional
 */
public record TokenRequest(
        String name,
        String id,
        String gateway,
        String expirationDate
) {
}
