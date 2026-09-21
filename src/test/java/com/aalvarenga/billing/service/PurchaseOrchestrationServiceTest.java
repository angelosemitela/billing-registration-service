package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.enums.ProductType;
import com.aalvarenga.billing.enums.ResultStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link PurchaseOrchestrationService}: orquestração da PERSISTÊNCIA de
 * uma compra já validada (ver javadoc da classe - nenhuma regra de negócio é
 * decidida aqui, só a ORDEM das chamadas e a montagem da resposta).
 *
 * <p>Todas as dependências ({@link AccountService}, {@link PaymentService},
 * {@link ProductService}, {@link BillingService}) são mockadas: o que este
 * teste precisa provar é que a orquestração as chama corretamente e traduz o
 * resultado delas para {@link PurchaseResponse}, não repetir a cobertura já
 * existente de cada uma delas isoladamente.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseOrchestrationServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private ProductService productService;
    @Mock
    private BillingService billingService;

    private PurchaseOrchestrationService service;

    private static final Long ACCOUNT_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrchestrationService(accountService, paymentService, productService, billingService);
        when(accountService.resolveAccount(any(), any())).thenReturn(AccountEntity.builder().id(ACCOUNT_ID).build());
    }

    private AccountRequest accountRequest() {
        return new AccountRequest(null, "João", "EXT-1", null, null, null, "joao@mail.com", true);
    }

    private ProductRequest productRequest(String codeId) {
        return new ProductRequest(codeId, "Produto", ProductType.ONESHOT, false, null, BigDecimal.TEN, BigDecimal.ZERO, null, "BRL", false, null);
    }

    private BillingRequest billingRequest(String codeId, BigDecimal chargedValue) {
        return new BillingRequest(codeId, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, chargedValue,
                "BRL", "TXN-1", 1, "PROVIDER", PaymentMethod.CREDIT, null);
    }

    private PurchaseRequest purchaseRequest(List<PaymentRequest> payments, List<BillingRequest> billings) {
        return new PurchaseRequest("WEB", "1800000000000", "PROTO-1",
                List.of(accountRequest()), List.of(productRequest("1")), payments, billings);
    }

    private ProductEntity productEntity() {
        return ProductEntity.builder().id(2L).productId("1").name("Produto").trial(false).build();
    }

    @Test
    void persistAndBuildResponse_callsAccountServiceForDocumentsAddressesAndPhonesWithResolvedAccountId() {
        when(productService.persistProducts(any(), anyLong(), any(), any(), eq(ACCOUNT_ID))).thenReturn(Map.of("1", productEntity()));

        service.persistAndBuildResponse(purchaseRequest(null, null), new ValidatedPurchaseContext(1_800_000_000_000L, null));

        verify(accountService).upsertDocuments(any(), eq(ACCOUNT_ID));
        verify(accountService).insertAddresses(any(), eq(ACCOUNT_ID));
        verify(accountService).insertPhones(any(), eq(ACCOUNT_ID));
    }

    @Test
    void persistAndBuildResponse_noBillingAndNoCharge_paymentAndBillingItemsAreNull() {
        when(productService.persistProducts(any(), anyLong(), any(), any(), eq(ACCOUNT_ID))).thenReturn(Map.of("1", productEntity()));
        when(paymentService.persistPayments(any(), eq(ACCOUNT_ID))).thenReturn(List.of());
        when(billingService.persistBillings(any(), any(), any())).thenReturn(List.of());

        PurchaseResponse response = service.persistAndBuildResponse(purchaseRequest(null, null), new ValidatedPurchaseContext(1_800_000_000_000L, null));

        assertThat(response.result()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(response.payment()).isNull();
        assertThat(response.billing()).isNull();
        assertThat(response.account()).hasSize(1);
        assertThat(response.account().getFirst().id()).isEqualTo("ACCT_1");
    }

    @Test
    void persistAndBuildResponse_hasEffectiveChargeAndPayments_paymentItemsArePopulated() {
        List<PaymentEntity> payments = List.of(PaymentEntity.builder().id(5L).method("CREDIT").defaultMethod(true).build());
        when(productService.persistProducts(any(), anyLong(), any(), any(), eq(ACCOUNT_ID))).thenReturn(Map.of("1", productEntity()));
        when(paymentService.persistPayments(any(), eq(ACCOUNT_ID))).thenReturn(payments);
        when(billingService.persistBillings(any(), any(), any())).thenReturn(List.of());

        PurchaseResponse response = service.persistAndBuildResponse(
                purchaseRequest(List.of(), List.of(billingRequest("1", BigDecimal.TEN))),
                new ValidatedPurchaseContext(1_800_000_000_000L, null));

        assertThat(response.payment()).hasSize(1);
        assertThat(response.payment().getFirst().method()).isEqualTo("CREDIT");
        assertThat(response.payment().getFirst().isDefault()).isTrue();
    }

    @Test
    void persistAndBuildResponse_noEffectiveCharge_paymentItemsAreNullEvenWithPaymentsPersisted() {
        // hasEffectiveCharge = soma de billing.chargedValue > 0 - mesmo com
        // pagamentos persistidos, se não houve cobrança efetiva o campo
        // "payment" da resposta continua null (ver javadoc de
        // PurchaseOrchestrationService.buildSuccessResponse).
        List<PaymentEntity> payments = List.of(PaymentEntity.builder().id(5L).method("CREDIT").defaultMethod(true).build());
        when(productService.persistProducts(any(), anyLong(), any(), any(), eq(ACCOUNT_ID))).thenReturn(Map.of("1", productEntity()));
        when(paymentService.persistPayments(any(), eq(ACCOUNT_ID))).thenReturn(payments);
        when(billingService.persistBillings(any(), any(), any())).thenReturn(List.of());

        PurchaseResponse response = service.persistAndBuildResponse(purchaseRequest(List.of(), null), new ValidatedPurchaseContext(1_800_000_000_000L, null));

        assertThat(response.payment()).isNull();
    }

    @Test
    void persistAndBuildResponse_withBills_billingItemsArePopulatedWithProductName() {
        BillEntity bill = BillEntity.builder().id(9L).codeId("1").paymentMethod("CREDIT").chargedValue(BigDecimal.TEN).currency("BRL").build();
        when(productService.persistProducts(any(), anyLong(), any(), any(), eq(ACCOUNT_ID))).thenReturn(Map.of("1", productEntity()));
        when(paymentService.persistPayments(any(), eq(ACCOUNT_ID))).thenReturn(List.of());
        when(billingService.persistBillings(any(), any(), any())).thenReturn(List.of(bill));

        PurchaseResponse response = service.persistAndBuildResponse(
                purchaseRequest(List.of(), List.of(billingRequest("1", BigDecimal.TEN))),
                new ValidatedPurchaseContext(1_800_000_000_000L, null));

        assertThat(response.billing()).hasSize(1);
        assertThat(response.billing().getFirst().codeId()).isEqualTo("1");
        assertThat(response.billing().getFirst().product()).isEqualTo("Produto");
        assertThat(response.billing().getFirst().id()).isEqualTo("BILL_9");
    }

    @Test
    void persistAndBuildResponse_noPaymentMarkedAsDefault_defaultPaymentPassedToProductServiceIsNull() {
        List<PaymentEntity> payments = List.of(PaymentEntity.builder().id(5L).method("PIX").defaultMethod(false).build());
        when(paymentService.persistPayments(any(), eq(ACCOUNT_ID))).thenReturn(payments);
        when(productService.persistProducts(any(), anyLong(), any(), isNull(), eq(ACCOUNT_ID))).thenReturn(Map.of("1", productEntity()));
        when(billingService.persistBillings(any(), any(), any())).thenReturn(List.of());

        service.persistAndBuildResponse(purchaseRequest(List.of(), null), new ValidatedPurchaseContext(1_800_000_000_000L, null));

        verify(productService).persistProducts(any(), anyLong(), any(), isNull(), eq(ACCOUNT_ID));
    }

    @Test
    void persistAndBuildResponse_twoPaymentsWithSameMethod_defaultOneIsStillFoundAndBothAppearInResponse() {
        // Regressão do bug reportado em 21/09/2026 (ver decisoes.md): antes da
        // correção, paymentService.persistPayments devolvia um
        // Map<PaymentMethod, PaymentEntity> - com 2 pagamentos CREDIT na
        // mesma compra, o segundo sobrescrevia o primeiro no mapa e o
        // pagamento isDefault=true (o primeiro) "desaparecia": o defaultPayment
        // repassado a productService.persistProducts virava null (por isso
        // T_PRODUCT.DEFAULT_PAYMENT_ID ficava null) e a resposta só trazia 1
        // dos 2 pagamentos. Com a List, os dois sobrevivem e o default
        // continua sendo encontrado independente da ordem/method repetido.
        PaymentEntity firstIsDefault = PaymentEntity.builder().id(60L).method("CREDIT").defaultMethod(true).build();
        PaymentEntity secondNotDefault = PaymentEntity.builder().id(61L).method("CREDIT").defaultMethod(false).build();
        List<PaymentEntity> payments = List.of(firstIsDefault, secondNotDefault);
        when(paymentService.persistPayments(any(), eq(ACCOUNT_ID))).thenReturn(payments);
        when(productService.persistProducts(any(), anyLong(), any(), eq(firstIsDefault), eq(ACCOUNT_ID))).thenReturn(Map.of("1", productEntity()));
        when(billingService.persistBillings(any(), any(), any())).thenReturn(List.of());

        PurchaseResponse response = service.persistAndBuildResponse(
                purchaseRequest(List.of(), List.of(billingRequest("1", BigDecimal.TEN))),
                new ValidatedPurchaseContext(1_800_000_000_000L, null));

        verify(productService).persistProducts(any(), anyLong(), any(), eq(firstIsDefault), eq(ACCOUNT_ID));
        assertThat(response.payment()).hasSize(2);
    }
}
