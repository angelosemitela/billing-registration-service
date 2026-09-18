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
 * Mapeia {@code T_DOMAIN_ACCOUNT_STATUS}. Diferente de
 * {@link CountryDomainEntity}/{@link CurrencyDomainEntity} (usadas só para
 * VALIDAR um código de entrada), esta entidade existe para a operação
 * inversa: dado o {@code STATUS} numérico já gravado em {@code T_ACCOUNT},
 * traduzir para o {@code BACKEND_VALUE} (ex: {@code "ACTIVE"}) que a
 * consulta de dados ({@code PurchaseQueryService}) devolve como
 * {@code accountSt} - ver {@code DomainStatusLookupService}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "T_DOMAIN_ACCOUNT_STATUS")
public class DomainAccountStatusEntity {

    @Id
    @Column(name = "STATUS_ID")
    private Integer statusId;

    @Column(name = "STATUS_DESC", nullable = false, length = 100)
    private String statusDesc;

    @Column(name = "BACKEND_VALUE", nullable = false, length = 30)
    private String backendValue;
}
