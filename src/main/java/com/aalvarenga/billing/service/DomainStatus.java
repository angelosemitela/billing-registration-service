package com.aalvarenga.billing.service;

/**
 * Constantes com os IDs das tabelas de domínio de status (T_DOMAIN_*_STATUS),
 * exatamente como semeados em {@code V1__create_domain_schema_and_seed_data.sql}.
 *
 * <p>Usar constantes nomeadas em vez de espalhar números "mágicos" (1, 2, 4...)
 * pelo código deixa cada regra de negócio autoexplicativa (ex:
 * {@code DomainStatus.PRODUCT_SOLD_WITHOUT_SERVICE} é muito mais claro do
 * que um {@code 4} solto) - também facilita achar todos os usos de um status
 * quando o negócio evoluir.
 */
public final class DomainStatus {

    private DomainStatus() {
    }

    // T_DOMAIN_ACCOUNT_STATUS / T_DOMAIN_ACCOUNT_DOCUMENT_STATUS /
    // T_DOMAIN_ACCOUNT_ADDRESS_STATUS / T_DOMAIN_ACCOUNT_PHONE_STATUS (1=Ativo, 2=Cancelado)
    public static final int ACTIVE = 1;

    // T_DOMAIN_PRODUCT_STATUS
    public static final int PRODUCT_ACTIVE = 1;
    public static final int PRODUCT_SUSPENDED = 2;
    public static final int PRODUCT_CANCELLED = 3;
    /** Venda com {@code isExpiriationService = false}: produto sem controle de vigência. */
    public static final int PRODUCT_SOLD_WITHOUT_SERVICE = 4;

    // T_DOMAIN_DISCOUNT_STATUS (1=Ativo)
    public static final int DISCOUNT_ACTIVE = 1;

    // T_DOMAIN_PAYMENT_STATUS / T_DOMAIN_PAYMENT_TOKEN_STATUS (1=Ativo)
    public static final int PAYMENT_ACTIVE = 1;
    public static final int PAYMENT_TOKEN_ACTIVE = 1;

    // T_DOMAIN_BILL_STATUS
    /** O enunciado define explicitamente o status inicial da fatura como "4 - Paga aguardando repasse". */
    public static final int BILL_PAID_AWAITING_TRANSFER = 4;

    // T_DOMAIN_BILL_TYPE
    /** Esta primeira versão do serviço só lida com a fatura da compra original. */
    public static final int BILL_TYPE_PURCHASE = 1;

    // T_DOMAIN_BILL_INSTALLMENT_STATUS
    public static final int BILL_INSTALLMENT_AWAITING_TRANSFER = 1;

    // T_DOMAIN_PRODUCT_SUSPENSION_STATUS (criada em V12, a pedido do usuário
    // em 21/09/2026 - ver README, seção "Evoluções pedidas"). Até a evolução
    // de 21/09 só existia o valor "Adimplente" (nenhum fluxo desta v1 vende
    // um produto já inadimplente, nem marca um existente como tal); o valor
    // "Inadimplente" passou a ser efetivamente LIDO (nunca gravado, só
    // consultado) a partir da feature de cancelamento (22/09/2026) - ver
    // CancellationValidationService, Regra 1 do tipo IMMEDIATE.
    public static final int PRODUCT_SUSPENSION_COMPLIANT = 1;
    public static final int PRODUCT_SUSPENSION_DEFAULTER = 2;

    // T_DOMAIN_PRODUCT_CANCELLATION_STATUS (V12). Os dois primeiros valores
    // já existiam desde a evolução de 21/09/2026 (ProductService reaproveita
    // a MESMA condição já usada para CANCELLATION_REQ_DT/CANCELLATION_SCH_DT
    // de V9: produto ONESHOT com vigência E o feature toggle
    // AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT ligado). O terceiro
    // ("Cancelado") só passou a ser GRAVADO a partir da feature de
    // cancelamento (22/09/2026) - ver CancellationOrchestrationService,
    // fluxo IMMEDIATE.
    public static final int PRODUCT_CANCELLATION_NO_SCHEDULES = 1;
    public static final int PRODUCT_CANCELLATION_SCHEDULED = 2;
    public static final int PRODUCT_CANCELLATION_CANCELLED = 3;

    // T_DOMAIN_BILL_REFUND_STATUS (V12). Toda fatura nasce sem estorno (ver
    // BillingService.persistBillings) - os valores de estorno PARCIAL/TOTAL
    // só passaram a ser gravados a partir da feature de cancelamento
    // (22/09/2026) - ver CancellationOrchestrationService.
    public static final int BILL_REFUND_NO_REFUND = 1;
    public static final int BILL_REFUND_PART_REFUNDED = 2;
    public static final int BILL_REFUND_FULL_REFUNDED = 3;

    // T_DOMAIN_BILL_STATUS - valor acrescentado em V15__add_bill_status_returned.sql
    // (22/09/2026), usado quando um estorno TOTAL "devolve" uma fatura - ver
    // CancellationOrchestrationService.
    public static final int BILL_RETURNED = 8;
}
