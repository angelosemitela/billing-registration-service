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
 * Mapeia {@code T_PAYMENT_TOKEN}.
 *
 * <p><b>Nota de inconsistência corrigida</b>: adicionamos {@link #paymentId}
 * (FK para T_PAYMENT), ausente no enunciado original, pois sem ela não
 * haveria como saber a qual método de pagamento um token pertence.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_PAYMENT_TOKEN")
public class PaymentTokenEntity extends BaseAuditableEntity {

    @Column(name = "PAYMENT_ID", nullable = false)
    private Long paymentId;

    @Column(name = "NAME", nullable = false, length = 100)
    private String name;

    @Column(name = "TOKEN", nullable = false, length = 200, unique = true)
    private String token;

    @Column(name = "GATEWAY", nullable = false, length = 100)
    private String gateway;

    @Column(name = "EXPIRATION_DT")
    private Long expirationDt;

    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
