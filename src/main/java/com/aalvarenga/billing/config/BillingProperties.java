package com.aalvarenga.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agrupa parâmetros de negócio configuráveis via application.yml sob o
 * prefixo "billing.*", evitando "números mágicos" espalhados pelo código.
 *
 * <p>Usar {@code @ConfigurationProperties} (em vez de {@code @Value} espalhado
 * pelas classes) é considerado boa prática porque: (1) centraliza a
 * configuração em um único lugar tipado, (2) permite validação e (3) facilita
 * localizar "quem usa o quê" quando o projeto crescer com novos serviços.
 *
 * @param log agrupamento de configurações relacionadas ao log de requisições (T_LOG)
 */
@ConfigurationProperties(prefix = "billing")
public record BillingProperties(Log log) {

    /**
     * @param maxPayloadLength tamanho máximo, em caracteres, aceito pelas
     *                         colunas INPUT/OUTPUT da tabela T_LOG antes de
     *                         truncar o conteúdo.
     */
    public record Log(int maxPayloadLength) {
    }
}
