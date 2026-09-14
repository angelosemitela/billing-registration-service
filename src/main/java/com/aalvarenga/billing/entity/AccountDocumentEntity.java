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

/**
 * Mapeia {@code T_ACCOUNT_DOCUMENT}. Existe um UNIQUE(ACCOUNT_ID, TYPE) no
 * banco: se a conta já tiver um documento daquele TYPE, a regra de negócio
 * (ver AccountService) faz UPDATE em vez de INSERT.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_ACCOUNT_DOCUMENT")
public class AccountDocumentEntity extends BaseAuditableEntity {

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "DESCRIPTION", length = 200)
    private String description;

    @Column(name = "VALUE", nullable = false, length = 100)
    private String value;

    @Column(name = "COUNTRY", nullable = false, length = 2)
    private String country;

    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
