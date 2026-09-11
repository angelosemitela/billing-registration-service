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
 * Mapeia a tabela de domínio {@code T_DOMAIN_CURRENCY} (ISO 4217). Usada
 * apenas para validar se um código de moeda informado na entrada existe.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "T_DOMAIN_CURRENCY")
public class CurrencyDomainEntity {

    @Id
    @Column(name = "CODE", length = 3)
    private String code;

    @Column(name = "CURRENCY", nullable = false, length = 100)
    private String currency;
}
