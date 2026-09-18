package com.aalvarenga.billing.service;

import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.ProductEntity;

/**
 * Resultado da validação de {@code PurchaseQueryRequest}, reaproveitado por
 * {@link PurchaseQueryService} - mesmo racional de {@link ValidatedPurchaseContext}:
 * evita repetir, na orquestração, buscas que a validação já fez.
 *
 * @param account            conta já resolvida (via {@code externalId} direto,
 *                           ou via {@code productId} -&gt; {@code product.accountId})
 * @param productFilter      {@code null} quando a consulta foi feita por
 *                           {@code externalId} (retorna tudo da conta);
 *                           quando a consulta foi feita por {@code productId},
 *                           o produto já resolvido - {@code products}/{@code payment}/
 *                           {@code bill} da resposta ficam restritos a ele
 * @param returnProductData  já com o valor padrão ({@code true}) aplicado
 * @param returnPaymentData  já com o valor padrão ({@code true}) aplicado
 * @param returnBillData     já com o valor padrão ({@code true}) aplicado
 * @param maxBillReturn      já com o valor padrão ({@code 0} = sem limite) aplicado
 */
public record ValidatedQueryContext(
        AccountEntity account,
        ProductEntity productFilter,
        boolean returnProductData,
        boolean returnPaymentData,
        boolean returnBillData,
        int maxBillReturn
) {
}
