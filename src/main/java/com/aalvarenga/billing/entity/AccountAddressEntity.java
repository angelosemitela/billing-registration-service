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

/**
 * Mapeia {@code T_ACCOUNT_ADDRESS}. Diferente de documentos, todo novo
 * endereço informado é sempre INSERIDO (nunca atualiza um existente),
 * conforme instrução explícita do enunciado.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "T_ACCOUNT_ADDRESS")
public class AccountAddressEntity extends BaseAuditableEntity {

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Column(name = "DESCRIPTION", nullable = false, length = 200)
    private String description;

    @Column(name = "ADDRESS_NAME", nullable = false, length = 200)
    private String addressName;

    @Column(name = "NUMBER", nullable = false, length = 20)
    private String number;

    @Column(name = "COMPLEMENT", length = 200)
    private String complement;

    @Column(name = "ZIP_CODE", nullable = false, length = 20)
    private String zipCode;

    @Column(name = "COUNTRY", nullable = false, length = 2)
    private String country;

    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
