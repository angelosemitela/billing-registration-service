package com.aalvarenga.billing.dto.request;

import java.math.BigDecimal;

/**
 * Um item da estrutura {@code refund} da entrada de cancelamento - o estorno
 * a ser aplicado a UMA fatura específica.
 *
 * <p>Sem anotações de Bean Validation de propósito, mesmo padrão já usado em
 * {@code ProductRequest}/{@code BillingRequest}: as regras aqui (formato do
 * ID, valor mínimo, elegibilidade da fatura) são regras de NEGÓCIO
 * (dependem de consulta ao banco e de outros campos), não apenas estruturais
 * - ficam centralizadas em {@code CancellationValidationService}.
 *
 * @param billId identificador da fatura a ser estornada. Aceita tanto o
 *               formato devolvido pela própria API ({@code "BILL_1"}) quanto
 *               o ID técnico puro ({@code "1"}) - ver {@code AssetIdParser}.
 * @param amount valor a ser estornado nesta fatura. Obrigatório, deve ser
 *               maior que zero e não pode exceder o saldo ainda estornável
 *               da fatura ({@code T_BILL.CHARGED_VALUE - T_BILL.REFUND_VALUE}).
 */
public record RefundRequestItem(
        String billId,
        BigDecimal amount
) {
}
