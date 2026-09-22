package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.BillEntity;

import java.math.BigDecimal;

/**
 * Um item de estorno já VALIDADO por {@link CancellationValidationService}:
 * a fatura já foi resolvida no banco (existe, pertence ao produto informado,
 * está paga e tem saldo estornável suficiente) e o valor já foi conferido.
 *
 * <p>Reaproveitado por {@link CancellationOrchestrationService}, que só
 * precisa APLICAR a mutação (somar ao {@code REFUND_VALUE}, recalcular o
 * {@code REFUND_STATUS}) - nenhuma regra de negócio é reavaliada na
 * persistência, mesmo padrão já usado por {@link ValidatedPurchaseContext}/
 * {@link ValidatedQueryContext}.
 *
 * @param bill   fatura já persistida, alvo do estorno
 * @param amount valor a estornar nesta fatura (já validado)
 */
public record ResolvedRefundItem(BillEntity bill, BigDecimal amount) {
}
