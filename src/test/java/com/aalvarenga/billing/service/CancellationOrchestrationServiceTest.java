package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.CancellationRequest;
import com.aalvarenga.billing.dto.response.CancellationResponse;
import com.aalvarenga.billing.dto.response.RefundResponseItem;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.CancellationType;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.ProductRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link CancellationOrchestrationService}: a mutação de
 * {@code T_PRODUCT}/{@code T_BILL} por tipo de cancelamento PROCESSADO
 * (já resolvido por {@link CancellationValidationService}) e a montagem do
 * {@link CancellationResponse}. Nenhuma regra de ELEGIBILIDADE é reavaliada
 * aqui (já coberta por {@code CancellationValidationServiceTest}) - o foco é
 * "dado um contexto já validado, o produto/faturas terminam no estado certo
 * e a resposta reflete esse estado".
 */
@ExtendWith(MockitoExtension.class)
class CancellationOrchestrationServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private DomainStatusLookupService domainStatusLookupService;

    private CancellationOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new CancellationOrchestrationService(productRepository, billRepository, domainStatusLookupService);
        lenient().when(productRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(billRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CancellationRequest request(CancellationType type) {
        return new CancellationRequest("WEB", "1700000000000", "PROTO-1", "PROD_1", type, "motivo", false, null);
    }

    private ProductEntity.ProductEntityBuilder<?, ?> baseProduct() {
        return ProductEntity.builder()
                .id(1L)
                .cycleStartDt(1_000L)
                .cycleEndDt(2_000_000L)
                .nextBillDt(3_000_000L)
                .status(DomainStatus.PRODUCT_ACTIVE)
                .cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES)
                .disableBilling(false)
                .autoCancelSch(false);
    }

    @Test
    void persistAndBuildResponse_immediate_updatesProductToCancelledAndDisablesBilling() {
        ProductEntity product = baseProduct().build();
        when(domainStatusLookupService.productStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLED, "CANCELLED"));
        when(domainStatusLookupService.productCancellationStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLATION_CANCELLED, "CANCELLED"));

        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.IMMEDIATE, CancellationType.IMMEDIATE, List.of());

        CancellationResponse response = service.persistAndBuildResponse(request(CancellationType.IMMEDIATE), context);

        assertThat(product.getStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLED);
        assertThat(product.getCancellationStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLATION_CANCELLED);
        assertThat(product.getDisableBilling()).isTrue();
        assertThat(product.getCancellationEfcDt()).isNotNull();
        assertThat(product.getCancellationChannel()).isEqualTo("WEB");
        assertThat(product.getCancellationDescription()).isEqualTo("motivo");
        assertThat(response.productSt()).isEqualTo("CANCELLED");
        assertThat(response.cancellationSt()).isEqualTo("CANCELLED");
        assertThat(response.productId()).isEqualTo("PROD_1");
        // nextBillDt só é preenchido para WITHDRAW_CANCELLATION.
        assertThat(response.nextBillDt()).isNull();
    }

    @Test
    void persistAndBuildResponse_scheduled_setsCancellationScheduledForCycleEnd() {
        ProductEntity product = baseProduct().build();
        when(domainStatusLookupService.productStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_ACTIVE, "ACTIVE"));
        when(domainStatusLookupService.productCancellationStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED, "SCHEDULED"));

        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.SCHEDULED, CancellationType.SCHEDULED, List.of());

        CancellationResponse response = service.persistAndBuildResponse(request(CancellationType.SCHEDULED), context);

        assertThat(product.getCancellationSchDt()).isEqualTo(2_000_000L);
        assertThat(product.getCancellationStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED);
        assertThat(product.getDisableBilling()).isTrue();
        // Status do produto não muda para SCHEDULED (só CANCELLATION_STATUS muda).
        assertThat(product.getStatus()).isEqualTo(DomainStatus.PRODUCT_ACTIVE);
        assertThat(response.cancellationDt()).isEqualTo("2000000");
    }

    @Test
    void persistAndBuildResponse_withdraw_clearsScheduleAndExposesNextBillDt() {
        ProductEntity product = baseProduct().cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED).cancellationSchDt(2_000_000L).build();
        when(domainStatusLookupService.productStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_ACTIVE, "ACTIVE"));
        when(domainStatusLookupService.productCancellationStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES, "NO_SCHEDULES"));

        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.WITHDRAW_CANCELLATION, CancellationType.WITHDRAW_CANCELLATION, List.of());

        CancellationResponse response = service.persistAndBuildResponse(request(CancellationType.WITHDRAW_CANCELLATION), context);

        assertThat(product.getCancellationSchDt()).isNull();
        assertThat(product.getCancellationStatus()).isEqualTo(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES);
        assertThat(product.getDisableBilling()).isFalse();
        assertThat(response.cancellationDt()).isNull();
        assertThat(response.nextBillDt()).isEqualTo("3000000");
    }

    @Test
    void persistAndBuildResponse_withPartialRefund_updatesBillAndReturnsRefundItem() {
        ProductEntity product = baseProduct().build();
        BillEntity bill = BillEntity.builder().id(9L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO).build();
        when(domainStatusLookupService.productStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLED, "CANCELLED"));
        when(domainStatusLookupService.productCancellationStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLATION_CANCELLED, "CANCELLED"));
        when(domainStatusLookupService.billRefundStatuses(any())).thenReturn(Map.of(DomainStatus.BILL_REFUND_PART_REFUNDED, "PART_REFUNDED"));

        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.IMMEDIATE, CancellationType.IMMEDIATE,
                List.of(new ResolvedRefundItem(bill, BigDecimal.valueOf(4))));

        CancellationResponse response = service.persistAndBuildResponse(request(CancellationType.IMMEDIATE), context);

        assertThat(bill.getRefundValue()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(bill.getRefundStatus()).isEqualTo(DomainStatus.BILL_REFUND_PART_REFUNDED);
        assertThat(bill.getStatus()).isNull(); // parcial: STATUS não é alterado
        assertThat(response.refund()).hasSize(1);
        RefundResponseItem item = response.refund().getFirst();
        assertThat(item.billId()).isEqualTo("BILL_9");
        assertThat(item.amount()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(item.refundSt()).isEqualTo("PART_REFUNDED");
        assertThat(item.updatedValue()).isEqualByComparingTo(BigDecimal.valueOf(6));
    }

    @Test
    void persistAndBuildResponse_withFullRefund_marksBillAsReturned() {
        ProductEntity product = baseProduct().build();
        BillEntity bill = BillEntity.builder().id(9L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO).build();
        when(domainStatusLookupService.productStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLED, "CANCELLED"));
        when(domainStatusLookupService.productCancellationStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLATION_CANCELLED, "CANCELLED"));
        when(domainStatusLookupService.billRefundStatuses(any())).thenReturn(Map.of(DomainStatus.BILL_REFUND_FULL_REFUNDED, "FULL_REFUNDED"));

        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.IMMEDIATE, CancellationType.IMMEDIATE,
                List.of(new ResolvedRefundItem(bill, BigDecimal.TEN)));

        CancellationResponse response = service.persistAndBuildResponse(request(CancellationType.IMMEDIATE), context);

        assertThat(bill.getRefundValue()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(bill.getRefundStatus()).isEqualTo(DomainStatus.BILL_REFUND_FULL_REFUNDED);
        assertThat(bill.getStatus()).isEqualTo(DomainStatus.BILL_RETURNED);
        assertThat(response.refund().getFirst().refundSt()).isEqualTo("FULL_REFUNDED");
        assertThat(response.refund().getFirst().updatedValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void persistAndBuildResponse_noRefunds_refundFieldIsNull() {
        ProductEntity product = baseProduct().build();
        when(domainStatusLookupService.productStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLED, "CANCELLED"));
        when(domainStatusLookupService.productCancellationStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLATION_CANCELLED, "CANCELLED"));

        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.IMMEDIATE, CancellationType.IMMEDIATE, List.of());

        CancellationResponse response = service.persistAndBuildResponse(request(CancellationType.IMMEDIATE), context);

        assertThat(response.refund()).isNull();
    }

    @Test
    void persistAndBuildResponse_immediateFromDowngradedScheduled_inputTypeStaysScheduledButProcessedIsImmediate() {
        ProductEntity product = baseProduct().build();
        when(domainStatusLookupService.productStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLED, "CANCELLED"));
        when(domainStatusLookupService.productCancellationStatuses(any())).thenReturn(Map.of(DomainStatus.PRODUCT_CANCELLATION_CANCELLED, "CANCELLED"));

        ValidatedCancellationContext context = new ValidatedCancellationContext(
                1_700_000_000_000L, product, CancellationType.SCHEDULED, CancellationType.IMMEDIATE, List.of());

        CancellationResponse response = service.persistAndBuildResponse(request(CancellationType.SCHEDULED), context);

        assertThat(response.inputType()).isEqualTo(CancellationType.SCHEDULED);
        assertThat(response.processedType()).isEqualTo(CancellationType.IMMEDIATE);
        verify(productRepository).save(product);
    }
}
