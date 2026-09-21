package com.aalvarenga.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mapeia {@code T_DOMAIN_PRODUCT_SUSPENSION_STATUS} (criada em
 * {@code V12__create_product_suspension_cancellation_refund_domains.sql}) -
 * ver javadoc de {@link DomainAccountStatusEntity} para o porquê desta
 * família de entidades existir (tradução de {@code STATUS} numérico para
 * {@code BACKEND_VALUE}, usada pela consulta de dados).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "T_DOMAIN_PRODUCT_SUSPENSION_STATUS")
public class DomainProductSuspensionStatusEntity {

    @Id
    @Column(name = "ID")
    private Integer id;

    @Column(name = "STATUS_DESC", nullable = false, length = 100)
    private String statusDesc;

    @Column(name = "BACKEND_VALUE", nullable = false, length = 30)
    private String backendValue;
}
