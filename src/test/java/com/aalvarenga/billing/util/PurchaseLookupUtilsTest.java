package com.aalvarenga.billing.util;

import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.enums.ProductType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link PurchaseLookupUtils} - buscas em memória sobre as listas do
 * payload de entrada, reaproveitadas TANTO pela validação quanto pela
 * persistência (ver javadoc da classe). Como é uma classe 100% de métodos
 * estáticos e sem estado, os testes aqui não precisam de Mockito: são só
 * "entrada -> saída esperada", incluindo os casos de lista/argumento nulo.
 */
class PurchaseLookupUtilsTest {

    private ProductRequest product(String codeId) {
        return new ProductRequest(codeId, "Produto " + codeId, ProductType.ONESHOT, false, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
    }

    private PaymentRequest payment(PaymentMethod method, boolean isDefault) {
        return new PaymentRequest(method, null, null, null, false, isDefault, 1, null, null);
    }

    private BillingRequest billing(BigDecimal chargedValue) {
        return new BillingRequest("1", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, chargedValue,
                "BRL", "TXN-1", 1, "PROVIDER", PaymentMethod.PIX, null);
    }

    @Test
    void findProductByCodeId_found() {
        List<ProductRequest> products = Arrays.asList(product("A"), product("B"));

        assertThat(PurchaseLookupUtils.findProductByCodeId(products, "B")).contains(product("B"));
    }

    @Test
    void findProductByCodeId_notFound() {
        List<ProductRequest> products = Collections.singletonList(product("A"));

        assertThat(PurchaseLookupUtils.findProductByCodeId(products, "Z")).isEmpty();
    }

    @Test
    void findProductByCodeId_nullListOrCodeId_isEmpty() {
        assertThat(PurchaseLookupUtils.findProductByCodeId(null, "A")).isEmpty();
        assertThat(PurchaseLookupUtils.findProductByCodeId(Collections.singletonList(product("A")), null)).isEmpty();
    }

    @Test
    void findPaymentByMethod_found() {
        List<PaymentRequest> payments = Arrays.asList(payment(PaymentMethod.CREDIT, true), payment(PaymentMethod.PIX, false));

        assertThat(PurchaseLookupUtils.findPaymentByMethod(payments, PaymentMethod.PIX))
                .map(PaymentRequest::method)
                .contains(PaymentMethod.PIX);
    }

    @Test
    void findPaymentByMethod_nullListOrMethod_isEmpty() {
        assertThat(PurchaseLookupUtils.findPaymentByMethod(null, PaymentMethod.PIX)).isEmpty();
        assertThat(PurchaseLookupUtils.findPaymentByMethod(Collections.singletonList(payment(PaymentMethod.PIX, true)), null)).isEmpty();
    }

    @Test
    void findDefaultPayment_returnsTheOneMarkedAsDefault() {
        List<PaymentRequest> payments = Arrays.asList(payment(PaymentMethod.CREDIT, false), payment(PaymentMethod.PIX, true));

        assertThat(PurchaseLookupUtils.findDefaultPayment(payments))
                .map(PaymentRequest::method)
                .contains(PaymentMethod.PIX);
    }

    @Test
    void findDefaultPayment_noneMarkedAsDefault_isEmpty() {
        List<PaymentRequest> payments = Collections.singletonList(payment(PaymentMethod.CREDIT, false));

        assertThat(PurchaseLookupUtils.findDefaultPayment(payments)).isEmpty();
    }

    @Test
    void findDefaultPayment_nullList_isEmpty() {
        assertThat(PurchaseLookupUtils.findDefaultPayment(null)).isEmpty();
    }

    @Test
    void sumChargedValues_sumsAllNonNullValues() {
        List<BillingRequest> billings = Arrays.asList(billing(new BigDecimal("10.00")), billing(new BigDecimal("5.50")));

        assertThat(PurchaseLookupUtils.sumChargedValues(billings)).isEqualByComparingTo("15.50");
    }

    @Test
    void sumChargedValues_ignoresNullChargedValueEntries() {
        // Uma fatura com chargedValue nulo não deveria acontecer na prática (é
        // obrigatório na validação), mas o método é null-safe mesmo assim.
        List<BillingRequest> billings = Arrays.asList(billing(new BigDecimal("10.00")), billing(null));

        assertThat(PurchaseLookupUtils.sumChargedValues(billings)).isEqualByComparingTo("10.00");
    }

    @Test
    void sumChargedValues_nullList_isZero() {
        assertThat(PurchaseLookupUtils.sumChargedValues(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sumChargedValues_emptyList_isZero() {
        assertThat(PurchaseLookupUtils.sumChargedValues(Collections.emptyList())).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
