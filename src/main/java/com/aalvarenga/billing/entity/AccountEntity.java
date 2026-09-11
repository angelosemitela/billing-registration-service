package com.aalvarenga.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Mapeia a tabela {@code T_ACCOUNT}: dados cadastrais do assinante/cliente.
 *
 * <p>Uma conta pode ser criada em uma requisição e reaproveitada (via
 * {@code account.id}) em requisições futuras, seja apenas para referência,
 * seja para atualização cadastral (ver com.aalvarenga.billing.service.AccountService).
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "T_ACCOUNT")
public class AccountEntity extends BaseAuditableEntity {

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "EXTERNAL_ID", nullable = false, length = 100, unique = true)
    private String externalId;

    /** FK lógica para T_DOMAIN_ACCOUNT_STATUS.STATUS_ID (1 = Ativo, 2 = Cancelado). */
    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
