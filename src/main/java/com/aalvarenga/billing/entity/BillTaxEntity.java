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

/** Mapeia {@code T_BILL_TAX}: detalhamento das taxas que compõem uma fatura. */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "T_BILL_TAX")
public class BillTaxEntity extends BaseAuditableEntity {

    @Column(name = "BILL_ID", nullable = false)
    private Long billId;

    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    @Column(name = "VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;
}
