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
 * Mapeia a tabela de domínio {@code T_DOMAIN_COUNTRY} (ISO 3166-1 alpha-2).
 * Usada apenas para VALIDAR se um código de país informado na entrada existe
 * (ver com.aalvarenga.billing.repository.CountryDomainRepository) - não tem
 * tabela de auditoria (tabelas de domínio ficam fora da regra de auditoria).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "T_DOMAIN_COUNTRY")
public class CountryDomainEntity {

    @Id
    @Column(name = "CODE", length = 2)
    private String code;

    @Column(name = "COUNTRY", nullable = false, length = 100)
    private String country;
}
