package com.aalvarenga.billing.dto.response;

import java.math.BigDecimal;

/**
 * Item de resposta para cada estorno processado por
 * {@code POST /api/v1/purchases/cancel}.
 *
 * @param billId       ID da fatura, formatado com o prefixo padrão da API
 *                     ({@code AssetIdFormatter.billing}) - canônico, mesmo
 *                     que a entrada tenha informado o ID técnico puro.
 * @param amount       valor de estorno solicitado nesta fatura (eco direto
 *                     da entrada, já validado).
 * @param refundSt     status de estorno da fatura APÓS a atualização, com
 *                     base em {@code T_DOMAIN_BILL_REFUND_STATUS.BACKEND_VALUE}.
 * @param updatedValue valor da fatura já descontado o estorno acumulado
 *                     ({@code T_BILL.CHARGED_VALUE - T_BILL.REFUND_VALUE},
 *                     após somar este estorno).
 */
public record RefundResponseItem(
        String billId,
        BigDecimal amount,
        String refundSt,
        BigDecimal updatedValue
) {
}
