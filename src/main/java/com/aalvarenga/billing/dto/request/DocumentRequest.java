package com.aalvarenga.billing.dto.request;

import com.aalvarenga.billing.enums.DocumentType;

/**
 * Estrutura {@code account.document} da entrada.
 *
 * @param type        tipo do documento (enum - qualquer valor fora do enum já
 *                    é rejeitado automaticamente pelo Jackson na desserialização)
 * @param description descrição livre (opcional)
 * @param value       valor/número do documento
 * @param country     país emissor (validado contra T_DOMAIN_COUNTRY)
 */
public record DocumentRequest(
        DocumentType type,
        String description,
        String value,
        String country
) {
}
