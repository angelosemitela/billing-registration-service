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
 * Mapeia {@code T_PAYMENT}: um método de pagamento informado na compra.
 *
 * <p><b>Nota de inconsistência corrigida</b> (ver README/ANALISE.md): o
 * enunciado original não define nenhuma coluna de vínculo desta tabela com
 * {@code T_ACCOUNT}. Adicionamos {@link #accountId} porque, sem ela, seria
 * impossível localizar os meios de pagamento salvos de um assinante para
 * cobranças futuras de recorrência.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_PAYMENT")
public class PaymentEntity extends BaseAuditableEntity {

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Column(name = "METHOD", nullable = false, length = 20)
    private String method;

    @Column(name = "ISSUER", length = 100)
    private String issuer;

    @Column(name = "CARD_NUMBER", length = 50)
    private String cardNumber;

    @Column(name = "EXPIRATION", length = 5)
    private String expiration;

    @Convert(converter = BooleanCharConverter.class)
    @Column(name = "MULTIPLE_B", length = 1)
    private Boolean multiple;

    @Convert(converter = BooleanCharConverter.class)
    @Column(name = "DEFAULT_B", nullable = false, length = 1)
    private Boolean defaultMethod;

    @Column(name = "INSTALLMENTS", nullable = false, length = 5)
    private String installments;

    @Column(name = "STATUS", nullable = false)
    private Integer status;

    /**
     * Bandeira do cartão (VISA/MASTERCARD/AMEX/ELO) - acrescentada em
     * V8__add_account_email_fallback_and_payment_brand.sql (17/09/2026).
     * NULLABLE porque só é obrigatória para METHOD = CREDIT/DEBIT (ver
     * PurchaseValidationService.validatePayments); PIX/WALLET nunca a
     * informam.
     */
    @Column(name = "BRAND", length = 20)
    private String brand;
}
