package com.aalvarenga.billing.enums;

/**
 * Modalidade de cancelamento de produto - campo {@code type} da entrada de
 * {@code POST /api/v1/purchases/cancel}.
 *
 * <ul>
 *   <li>{@link #IMMEDIATE} - cancela o produto imediatamente (efetiva agora);</li>
 *   <li>{@link #SCHEDULED} - agenda o cancelamento para o fim do ciclo de
 *       vigência atual ({@code T_PRODUCT.CYCLE_END_DT}) - só elegível para
 *       produtos com serviço associado ({@code isExpiriationService=true});</li>
 *   <li>{@link #WITHDRAW_CANCELLATION} - desiste de um cancelamento
 *       previamente AGENDADO (não desfaz um cancelamento já IMEDIATO/efetivado).</li>
 * </ul>
 *
 * <p><b>Importante</b>: o TIPO EFETIVAMENTE PROCESSADO pode ser diferente do
 * tipo informado na entrada - ver {@code CancellationValidationService}, que
 * "rebaixa" (downgrade) um {@code SCHEDULED} para {@code IMMEDIATE} quando o
 * {@code transactionDt} da requisição já é anterior ao início do ciclo atual
 * do produto (regra pensada para requisições assíncronas que chegam
 * atrasadas). A resposta expõe os dois valores separadamente
 * ({@code inputType}/{@code processedType}).
 */
public enum CancellationType {
    SCHEDULED,
    IMMEDIATE,
    WITHDRAW_CANCELLATION
}
