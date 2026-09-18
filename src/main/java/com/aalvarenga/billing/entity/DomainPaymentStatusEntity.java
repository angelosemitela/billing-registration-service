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
 * Mapeia {@code T_DOMAIN_PAYMENT_STATUS}. Diferente das tabelas de status de
 * conta/produto/desconto (cuja PK se chama {@code STATUS_ID}), esta tabela
 * segue o padrão usado pelo enunciado para pagamento/fatura, cuja PK se
 * chama simplesmente {@code ID} - ver
 * {@code V1__create_domain_schema_and_seed_data.sql}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "T_DOMAIN_PAYMENT_STATUS")
public class DomainPaymentStatusEntity {

    @Id
    @Column(name = "ID")
    private Integer id;

    @Column(name = "STATUS_DESC", nullable = false, length = 100)
    private String statusDesc;

    @Column(name = "BACKEND_VALUE", nullable = false, length = 30)
    private String backendValue;
}
