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
@SuperBuilder
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

    // Campos acrescentados em V8__add_account_email_fallback_and_payment_brand.sql
    // (17/09/2026) - diferente de NAME/EXTERNAL_ID, são obrigatórios em TODA
    // requisição (ver PurchaseValidationService.validateAccount), não só na
    // criação de conta nova.

    @Column(name = "EMAIL", nullable = false, length = 200)
    private String email;

    /** Se o assinante autoriza cobrança em método alternativo quando o principal falha (ex: sem sucesso no CREDIT, tenta no DEBIT). */
    @Convert(converter = BooleanCharConverter.class)
    @Column(name = "AUTHORIZED_FALLBACK_B", nullable = false, length = 1)
    private Boolean authorizedFallback;
}
