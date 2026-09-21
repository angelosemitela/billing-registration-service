package com.aalvarenga.billing.entity;

import jakarta.persistence.Column;
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
 * Mapeia {@code T_CONFIG_PARAMETERS}: parâmetros de configuração genéricos,
 * um {@link #value} de TEXTO por {@link #parameterName} (criada em
 * {@code V11__create_config_parameters.sql}, a pedido do usuário em
 * 21/09/2026 - ver README, seção "Evoluções pedidas").
 *
 * <p>Diferente de {@link ConfigFeatureToggleEntity} (que só liga/desliga uma
 * regra - um booleano), esta tabela guarda qualquer valor que uma regra de
 * negócio precise consultar em runtime sem exigir um novo deploy - a
 * interpretação do texto (uma data em epoch millis, um número, uma string
 * solta) fica por conta de quem consome (ver {@code ConfigParameterService}).
 *
 * <p>Também é auditada normalmente (tem {@code T_CONFIG_PARAMS_AU} +
 * triggers) pelo mesmo motivo de {@link ConfigFeatureToggleEntity}: o valor
 * de um parâmetro PODE mudar em produção, então vale a pena rastrear quem
 * mudou o quê.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_CONFIG_PARAMETERS")
public class ConfigParameterEntity extends BaseAuditableEntity {

    @Column(name = "PARAMETER_NAME", nullable = false, length = 100, unique = true)
    private String parameterName;

    @Column(name = "PARAMETER_DESCRIPTION", nullable = false, length = 500)
    private String parameterDescription;

    @Column(name = "VALUE", nullable = false, length = 200)
    private String value;
}
