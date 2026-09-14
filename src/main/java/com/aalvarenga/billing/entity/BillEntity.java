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
}
