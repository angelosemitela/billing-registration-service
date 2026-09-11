package com.aalvarenga.billing.util;

import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.enums.PaymentMethod;

import java.util.List;
import java.util.Optional;

/**
 * Pequenas buscas em memória sobre as listas do payload de entrada
 * (ex: "encontre o produto cujo codeId bate com esta fatura"), usadas TANTO
 * pela validação ({@code PurchaseValidationService}) QUANTO pela persistência
 * ({@code PurchaseOrchestrationService} e afins) - evita duplicar a mesma
 * lógica de busca nos dois lugares (reaproveitamento de código, instrução 3).
 *
 * <p>As listas do payload são sempre pequenas (poucos produtos/pagamentos por
 * compra), então uma busca linear simples é suficiente e mais legível do que
 * montar um {@code Map} para este caso de uso.
 */
public final class PurchaseLookupUtils {

    private PurchaseLookupUtils() {
    }

    public static Optional<ProductRequest> findProductByCodeId(List<ProductRequest> products, String codeId) {
        if (products == null || codeId == null) {
            return Optional.empty();
        }
        return products.stream().filter(p -> codeId.equals(p.codeId())).findFirst();
    }

    public static Optional<PaymentRequest> findPaymentByMethod(List<PaymentRequest> payments, PaymentMethod method) {
        if (payments == null || method == null) {
            return Optional.empty();
        }
        return payments.stream().filter(p -> method == p.method()).findFirst();
    }

    public static Optional<PaymentRequest> findDefaultPayment(List<PaymentRequest> payments) {
        if (payments == null) {
            return Optional.empty();
        }
        return payments.stream().filter(p -> Boolean.TRUE.equals(p.isDefault())).findFirst();
    }

    /** Soma de todas as {@code chargedValue} informadas nas faturas (usada para decidir se houve cobrança efetiva). */
    public static java.math.BigDecimal sumChargedValues(List<BillingRequest> billings) {
        if (billings == null) {
            return java.math.BigDecimal.ZERO;
        }
        return billings.stream()
                .map(BillingRequest::chargedValue)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
