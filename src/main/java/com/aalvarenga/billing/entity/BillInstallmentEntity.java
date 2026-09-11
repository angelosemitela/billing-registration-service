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

import java.math.BigDecimal;

/**
 * Mapeia {@code T_BILL_INSTALLMENT}: detalhamento do parcelamento de uma
 * fatura (só populada quando {@code installments > 1} - ver
 * com.aalvarenga.billing.service.InstallmentSplitService para a regra de
 * rateio de centavos).
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "T_BILL_INSTALLMENT")
public class BillInstallmentEntity extends BaseAuditableEntity {

    @Column(name = "BILL_ID", nullable = false)
    private Long billId;

    @Column(name = "INSTALLMENT", nullable = false)
    private Integer installment;

    @Column(name = "TOTAL_INSTALLMENT", nullable = false)
    private Integer totalInstallment;

    @Column(name = "PRODUCT_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal productValue;

    @Column(name = "DISCOUNT_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "TAX_VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxValue;

    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
