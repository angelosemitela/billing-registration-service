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

/** Mapeia {@code T_ACCOUNT_PHONE}. Assim como endereço, é sempre inserido (nunca atualizado). */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_ACCOUNT_PHONE")
public class AccountPhoneEntity extends BaseAuditableEntity {

    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    @Column(name = "NUMBER", nullable = false, length = 20)
    private String number;

    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
