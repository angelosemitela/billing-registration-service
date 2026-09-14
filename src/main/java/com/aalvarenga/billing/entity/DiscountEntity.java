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

/** Mapeia {@code T_DISCOUNT}: só é criada quando {@code discountValue > 0}. */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_DISCOUNT")
public class DiscountEntity extends BaseAuditableEntity {

    @Column(name = "PRODUCT_ID", nullable = false)
    private Long productId;

    @Column(name = "START_DT", nullable = false)
    private Long startDt;

    @Column(name = "END_DT", nullable = false)
    private Long endDt;

    @Column(name = "VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
