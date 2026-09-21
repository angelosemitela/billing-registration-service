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

import java.math.BigDecimal;

/**
 * Mapeia {@code T_BILL}: cada fatura gerada para um produto vendido (só é
 * persistida quando {@code chargedValue > 0}, conforme regra do enunciado).
 *
 * <p><b>Nota de inconsistência corrigida</b>: adicionamos {@link #paymentId}
 * (FK opcional para T_PAYMENT), permitindo rastrear qual meio de pagamento
 * específico (cartão/token) financiou esta fatura - o enunciado original só
 * guardava o método como texto solto ({@link #paymentMethod}).
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_BILL")
public class BillEntity extends BaseAuditableEntity {

    @Column(name = "CODE_ID", nullable = false, length = 100)
    private String codeId;

    @Column(name = "PRODUCT_ID", nullable = false)
    private Long productId;

    @Column(name = "PAYMENT_ID")
    private Long paymentId;

    @Column(name = "PRODUCT_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal productValue;

    @Column(name = "DISCOUNT_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "TAX_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxValue;

    @Column(name = "CHARGED_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal chargedValue;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "TRANSACTION_ID", nullable = false, length = 100, unique = true)
    private String transactionId;

    @Column(name = "INSTALLMENTS", nullable = false, length = 5)
    private String installments;

    @Column(name = "PROVIDER", nullable = false, length = 100)
    private String provider;

    @Column(name = "PAYMENT_METHOD", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "STATUS", nullable = false)
    private Integer status;

    @Column(name = "BILL_TYPE", nullable = false)
    private Integer billType;

    // Colunas adicionadas na V6__add_bill_cycle_and_payment_dates.sql.

    /** Início do ciclo ao qual esta fatura se refere - copiado do produto correspondente (mesma regra de {@link ProductEntity#getCycleStartDt()}). */
    @Column(name = "CYCLE_START_DT", nullable = false)
    private Long cycleStartDt;

    /** Fim do ciclo; {@code null} quando o produto não tem controle de vigência (mesma regra de {@link ProductEntity#getCycleEndDt()}). */
    @Column(name = "CYCLE_END_DT")
    private Long cycleEndDt;

    /** Data de vencimento do pagamento; no fluxo de "/api/v1/purchases" usa o mesmo valor de {@code TRANSACTION_DT}. */
    @Column(name = "DUE_DT", nullable = false)
    private Long dueDt;

    /** Data em que o repasse foi efetivado; sempre {@code null} na criação via "/api/v1/purchases". */
    @Column(name = "PAYMENT_DT")
    private Long paymentDt;

    // Colunas adicionadas na V14__add_bill_balance_and_refund_fields.sql
    // (21/09/2026) - ver README, seção "Evoluções pedidas". Toda fatura
    // nasce sem saldo em aberto e sem estorno - ver BillingService.

    /** Valor em aberto (não pago) da fatura; sempre {@code 0} na criação. */
    @Column(name = "BALANCE_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal balanceValue;

    /** Valor já estornado ao assinante; sempre {@code 0} na criação. */
    @Column(name = "REFUND_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal refundValue;

    /** FK lógica para {@code T_DOMAIN_BILL_REFUND_STATUS}; sempre {@code DomainStatus.BILL_REFUND_NO_REFUND} na criação. */
    @Column(name = "REFUND_STATUS", nullable = false)
    private Integer refundStatus;
}
