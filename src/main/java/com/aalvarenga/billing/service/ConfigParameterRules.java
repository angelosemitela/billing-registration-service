package com.aalvarenga.billing.service;

/**
 * Constantes com os nomes exatos ({@code PARAMETER_NAME}) dos parâmetros
 * semeados em {@code T_CONFIG_PARAMETERS} (ver
 * {@code V11__create_config_parameters.sql}).
 *
 * <p>Mesma motivação de {@link FeatureToggleRules}/{@link DomainStatus}: uma
 * constante nomeada evita espalhar a string mágica pelo código e evita erro
 * de digitação (ver {@link ConfigParameterService#getValue}).
 */
public final class ConfigParameterRules {

    private ConfigParameterRules() {
    }

    /**
     * Data mínima de {@code transactionDt} aceita em
     * {@code POST /api/v1/purchases}, em epoch milissegundos. Ver
     * {@code PurchaseValidationService.validateTransactionDt}.
     */
    public static final String MINIMAL_TRANSACTION_DATE = "MINIMAL_TRANSACTION_DATE";
}
