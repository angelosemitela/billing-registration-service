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
    // em 21/09/2026 - ver README, seção "Evoluções pedidas"). Hoje só existe
    // o valor "Adimplente": nenhum fluxo desta v1 vende um produto já
    // inadimplente, nem marca um produto existente como tal.
    public static final int PRODUCT_SUSPENSION_COMPLIANT = 1;

    // T_DOMAIN_PRODUCT_CANCELLATION_STATUS (V12). Ver ProductService -
    // reaproveita a MESMA condição já usada para CANCELLATION_REQ_DT/
    // CANCELLATION_SCH_DT (V9): produto ONESHOT com vigência E o feature
    // toggle AUTOMATIC_SCHEDULE_CANCEL_FOR_ONE_SHOT ligado.
    public static final int PRODUCT_CANCELLATION_NO_SCHEDULES = 1;
    public static final int PRODUCT_CANCELLATION_SCHEDULED = 2;

    // T_DOMAIN_BILL_REFUND_STATUS (V12). Toda fatura nasce sem estorno -
    // ver BillingService.persistBillings.
    public static final int BILL_REFUND_NO_REFUND = 1;
}
