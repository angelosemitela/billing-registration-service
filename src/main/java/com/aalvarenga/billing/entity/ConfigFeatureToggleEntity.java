package com.aalvarenga.billing.entity;

import com.aalvarenga.billing.entity.converter.BooleanCharConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Mapeia {@code T_CONFIG_FEATURE_TOGGLE}: liga/desliga, via configuração em
 * banco, regras de negócio que podem precisar ser ativadas ou desativadas
 * sem exigir um novo deploy da aplicação (criada em
 * {@code V10__create_config_feature_toggle.sql}, a pedido do usuário em
 * 17/09/2026 - ver README, seção "Evoluções pedidas").
 *
 * <p>Diferente das tabelas {@code T_DOMAIN_*} (que são apenas listas fixas de
 * valores para validação), esta tabela É auditada normalmente (tem
 * {@code T_CONFIG_FEATURE_TOGGLE_AU} + triggers) porque, ao contrário de um
 * domínio, o VALOR de {@link #status} pode mudar em produção (ex: um
 * administrador desativando temporariamente uma regra) - e essa mudança é
 * exatamente o tipo de evento que vale a pena auditar.
 *
 * <p>Cada regra é identificada por {@link #ruleName} (uma constante de texto
 * estável, ver {@code com.aalvarenga.billing.service.FeatureToggleRules}) -
 * nunca pelo {@code ID} técnico, que é só a chave primária da tabela.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_CONFIG_FEATURE_TOGGLE")
public class ConfigFeatureToggleEntity extends BaseAuditableEntity {

    @Column(name = "RULE_NAME", nullable = false, length = 100, unique = true)
    private String ruleName;

    @Column(name = "RULE_DESCRIPTION", nullable = false, length = 500)
    private String ruleDescription;

    @Convert(converter = BooleanCharConverter.class)
    @Column(name = "STATUS_B", nullable = false, length = 1)
    private Boolean status;
}
