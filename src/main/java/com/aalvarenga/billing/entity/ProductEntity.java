package com.aalvarenga.billing.entity;

import com.aalvarenga.billing.entity.converter.BooleanCharConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

/**
 * Mapeia {@code T_PRODUCT}: cada produto comprado em uma requisição vira 1
 * registro aqui, representando a "assinatura"/venda em si (com seu ciclo de
 * vigência e próxima data de recorrência, quando aplicável).
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, of = {})
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "T_PRODUCT")
public class ProductEntity extends BaseAuditableEntity {

    // FK para T_ACCOUNT - adicionada na V5__add_product_account_id.sql.
    // Sem esta coluna não havia como responder "quais produtos pertencem à
    // conta X" com uma consulta ao banco: a associação só existia "de
    // passagem", dentro do Map<codeId, ProductEntity> em memória usado por
    // PurchaseOrchestrationService durante o processamento de UMA requisição.
    @Column(name = "ACCOUNT_ID", nullable = false)
    private Long accountId;

    /** codeId informado na entrada (ver nota sobre a inconsistência id/codeId no README). */
    @Column(name = "PRODUCT_ID", nullable = false, length = 100)
    private String productId;

    @Column(name = "NAME", nullable = false, length = 200)
    private String name;

    @Column(name = "TYPE", nullable = false, length = 20)
    private String type;

    @Convert(converter = BooleanCharConverter.class)
    @Column(name = "EXP_SERV_B", nullable = false, length = 1)
    private Boolean expirationService;

    @Column(name = "RECURRENCE_FREQUENCY", length = 20)
    private String recurrenceFrequency;

    @Column(name = "VALUE", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Convert(converter = BooleanCharConverter.class)
    @Column(name = "TRIAL_B", nullable = false, length = 1)
    private Boolean trial;

    @Column(name = "TRIAL_DAYS")
    private Integer trialDays;

    @Column(name = "ASSET_ID", length = 100)
    private String assetId;

    @Column(name = "NEXT_BILL_DT")
    private Long nextBillDt;

    @Column(name = "FUTURE_BILL_DT")
    private Long futureBillDt;

    @Column(name = "CYCLE_START_DT", nullable = false)
    private Long cycleStartDt;

    @Column(name = "CYCLE_END_DT")
    private Long cycleEndDt;

    @Column(name = "DEFAULT_PAYMENT_ID", length = 100)
    private String defaultPaymentId;

    @Column(name = "CHANNEL", nullable = false, length = 50)
    private String channel;

    @Column(name = "TRANSACTION_DT", nullable = false)
    private Long transactionDt;

    @Column(name = "STATUS", nullable = false)
    private Integer status;
}
