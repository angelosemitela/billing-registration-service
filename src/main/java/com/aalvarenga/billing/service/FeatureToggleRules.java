package com.aalvarenga.billing.service;

/**
 * Constantes com os nomes exatos ({@code RULE_NAME}) das regras semeadas em
 * {@code T_CONFIG_FEATURE_TOGGLE} (ver
 * {@code V10__create_config_feature_toggle.sql}).
 *
 * <p>Mesma motivação de {@link DomainStatus}: usar uma constante nomeada em
 * vez de espalhar a string mágica {@code "AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT"}
 * pelo código deixa o uso autoexplicativo e evita erro de digitação (um typo
 * numa string solta faria {@link FeatureToggleService#isEnabled} nunca
 * encontrar a regra e silenciosamente tratá-la como desativada).
 */
public final class FeatureToggleRules {

    private FeatureToggleRules() {
    }

    /**
     * Quando ativada: para produtos {@code type=ONESHOT} com
     * {@code isExpiriationService=true}, agenda automaticamente o
     * cancelamento do produto ao final da vigência (preenche
     * {@code T_PRODUCT.CANCELLATION_REQ_DT}/{@code CANCELLATION_SCH_DT}).
     * Ver {@code ProductService.persistProducts}.
     */
    public static final String AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT = "AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT";
}
