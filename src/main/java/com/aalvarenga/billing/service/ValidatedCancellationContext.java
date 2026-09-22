package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.CancellationType;

import java.util.List;

/**
 * Resultado de {@link CancellationValidationService#validate}, reaproveitado
 * por {@link CancellationOrchestrationService} - mesmo padrão de
 * {@link ValidatedPurchaseContext}/{@link ValidatedQueryContext}: tudo que
 * já foi resolvido/calculado durante a validação (a entidade já carregada do
 * banco, o tipo efetivamente a ser processado, os estornos já validados) é
 * passado adiante, para a persistência nunca precisar refazer a mesma
 * consulta ou reavaliar a mesma regra.
 *
 * @param transactionDateEpochMillis {@code transactionDt} já convertida
 * @param product                    produto a ser cancelado, já carregado
 * @param inputType                  tipo de cancelamento exatamente como veio na entrada
 * @param processedType              tipo efetivamente processado (pode divergir de {@code inputType} - ver {@link CancellationType})
 * @param refunds                    estornos já validados; lista vazia quando a entrada não tinha {@code hasRefund=true}
 */
public record ValidatedCancellationContext(
        long transactionDateEpochMillis,
        ProductEntity product,
        CancellationType inputType,
        CancellationType processedType,
        List<ResolvedRefundItem> refunds
) {
}
