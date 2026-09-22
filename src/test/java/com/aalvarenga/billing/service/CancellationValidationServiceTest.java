package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.CancellationRequest;
import com.aalvarenga.billing.dto.request.RefundRequestItem;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.LogEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.CancellationType;
import com.aalvarenga.billing.exception.BusinessException;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.LogRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link CancellationValidationService}: todas as regras de negócio de
 * {@code POST /api/v1/purchases/cancel} (ver anexo "cancelamento.txt",
 * resumido no javadoc da classe validada).
 *
 * <p>Segue o mesmo padrão de {@code PurchaseValidationServiceTest}: cada
 * teste isola UMA regra, construindo o cenário mínimo necessário para
 * exercitá-la (as demais regras já validadas antes dela na cadeia são
 * satisfeitas com valores "neutros").
 */
@ExtendWith(MockitoExtension.class)
class CancellationValidationServiceTest {

    private static final long NOW = System.currentTimeMillis();
    private static final long TRANSACTION_DT = NOW - 1_000_000L;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private LogRepository logRepository;
    @Mock
    private ConfigParameterService configParameterService;

    private CancellationValidationService service;

    @BeforeEach
    void setUp() {
        service = new CancellationValidationService(productRepository, billRepository, logRepository, configParameterService);
        // lenient(): nem todo teste chega a exercitar estes 3 pontos (ex: um
        // teste que falha por transactionDt inválido nunca chega a consultar
        // idempotência/faturas), e alguns testes sobrescrevem estes stubs
        // com um argumento mais específico - lenient evita falso-positivo
        // de "unnecessary stubbing" do Mockito nesses casos.
        lenient().when(configParameterService.getLongValue(ConfigParameterRules.MINIMAL_TRANSACTION_DATE)).thenReturn(Optional.empty());
        lenient().when(logRepository.findByProtocol(any())).thenReturn(List.of());
        lenient().when(billRepository.findByProductIdInOrderByDueDtDesc(any())).thenReturn(List.of());
    }

    private CancellationRequest request(CancellationType type, Boolean hasRefund, List<RefundRequestItem> refund) {
        return new CancellationRequest("WEB", String.valueOf(TRANSACTION_DT), "PROTO-1", "PROD_1", type, "desc", hasRefund, refund);
    }

    private ProductEntity.ProductEntityBuilder<?, ?> baseProduct() {
        return ProductEntity.builder()
                .id(1L)
                .accountId(1L)
                .productId("codeId-1")
                .name("Produto")
                .type("ONESHOT")
                .expirationService(true)
                .value(BigDecimal.TEN)
                .currency("BRL")
                .trial(false)
                // Bem anterior a TRANSACTION_DT (e a qualquer cycleStartDt sobrescrito
                // pelos testes abaixo) de propósito: CREATED_DT precisa ficar fora do
                // caminho de quem testa outras regras - só os testes da nova regra
                // "transactionDt >= CREATED_DT" (ver validateTransactionDtAgainstProductCreation)
                // sobrescrevem este valor.
                .createdDt(TRANSACTION_DT - 50_000L)
                .cycleStartDt(TRANSACTION_DT - 10_000L)
                .cycleEndDt(TRANSACTION_DT + 10_000_000L)
                .transactionDt(TRANSACTION_DT - 10_000L)
                .channel("WEB")
                .status(DomainStatus.PRODUCT_ACTIVE)
                .disableBilling(false)
                .suspensionStatus(DomainStatus.PRODUCT_SUSPENSION_COMPLIANT)
                .cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES)
                .autoCancelSch(false);
    }

    // ------------------------------------------------------------------
    // transactionDt / protocolId / type / productId
    // ------------------------------------------------------------------

    @Test
    void validate_invalidTransactionDt_throwsBadRequest() {
        CancellationRequest request = new CancellationRequest("WEB", "not-a-number", "PROTO-1", "PROD_1", CancellationType.IMMEDIATE, null, false, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validate_futureTransactionDt_throwsBadRequest() {
        CancellationRequest request = new CancellationRequest("WEB", String.valueOf(NOW + 10_000_000L), "PROTO-1", "PROD_1",
                CancellationType.IMMEDIATE, null, false, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be in the future");
    }

    @Test
    void validate_transactionDtBelowConfiguredMinimum_throwsBadRequest() {
        when(configParameterService.getLongValue(ConfigParameterRules.MINIMAL_TRANSACTION_DATE)).thenReturn(Optional.of(TRANSACTION_DT + 1));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minimum accepted date");
    }

    @Test
    void validate_protocolIdAlreadySucceeded_throwsConflict() {
        LogEntity successLog = LogEntity.builder().protocol("PROTO-1").result("SUCCESS").build();
        when(logRepository.findByProtocol("PROTO-1")).thenReturn(List.of(successLog));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void validate_protocolIdOnlyHasErrorLogs_isNotBlocked() {
        // Mesma semântica de idempotência de PurchaseValidationService: um
        // protocolId cujo único histórico é de ERRO pode ser reprocessado -
        // a requisição segue até o fim sem lançar o conflito 409.
        LogEntity errorLog = LogEntity.builder().protocol("PROTO-1").result("ERROR").build();
        when(logRepository.findByProtocol("PROTO-1")).thenReturn(List.of(errorLog));
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));

        assertThatCode(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_missingType_throwsBadRequest() {
        CancellationRequest request = new CancellationRequest("WEB", String.valueOf(TRANSACTION_DT), "PROTO-1", "PROD_1", null, null, false, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void validate_productIdNotParseable_throwsNotFound() {
        CancellationRequest request = new CancellationRequest("WEB", String.valueOf(TRANSACTION_DT), "PROTO-1", "not-numeric",
                CancellationType.IMMEDIATE, null, false, null);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void validate_productIdNotFoundInDatabase_throwsNotFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void validate_productIdAsRawTechnicalId_isAlsoAccepted() {
        CancellationRequest request = new CancellationRequest("WEB", String.valueOf(TRANSACTION_DT), "PROTO-1", "1",
                CancellationType.IMMEDIATE, null, false, null);
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));

        ValidatedCancellationContext context = service.validate(request);

        assertThat(context.product().getId()).isEqualTo(1L);
    }

    @Test
    void validate_transactionDtEarlierThanProductCreatedDt_throwsBadRequest() {
        // Regra nova: transactionDt não pode ser anterior a T_PRODUCT.CREATED_DT
        // do produto resolvido - um cancelamento não pode "acontecer" antes de o
        // produto sequer ter sido criado. Só pode ser checada DEPOIS de resolver
        // o produto, por isso o cenário força CREATED_DT a ficar depois de
        // TRANSACTION_DT (o valor usado pelo helper request()).
        ProductEntity product = baseProduct().createdDt(TRANSACTION_DT + 1).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .hasMessageContaining("product's creation date");
    }

    @Test
    void validate_transactionDtEqualToProductCreatedDt_isAccepted() {
        // Limite exato (>=, não >): transactionDt == CREATED_DT deve passar.
        ProductEntity product = baseProduct().createdDt(TRANSACTION_DT).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatCode(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_transactionDtEarlierThanProductCreatedDt_appliesToAllThreeTypes() {
        // A regra vale para os 3 tipos de cancelamento, não só IMMEDIATE -
        // verificado aqui para SCHEDULED e WITHDRAW_CANCELLATION também.
        ProductEntity productForScheduled = baseProduct().createdDt(TRANSACTION_DT + 1).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(productForScheduled));
        assertThatThrownBy(() -> service.validate(request(CancellationType.SCHEDULED, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product's creation date");

        ProductEntity productForWithdraw = baseProduct().createdDt(TRANSACTION_DT + 1)
                .cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(productForWithdraw));
        assertThatThrownBy(() -> service.validate(request(CancellationType.WITHDRAW_CANCELLATION, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product's creation date");
    }

    // ------------------------------------------------------------------
    // IMMEDIATE
    // ------------------------------------------------------------------

    @Test
    void validate_immediate_happyPath_processesAsImmediate() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));

        ValidatedCancellationContext context = service.validate(request(CancellationType.IMMEDIATE, false, null));

        assertThat(context.processedType()).isEqualTo(CancellationType.IMMEDIATE);
        assertThat(context.inputType()).isEqualTo(CancellationType.IMMEDIATE);
    }

    @Test
    void validate_immediate_defaultingSubscriber_throwsBadRequest() {
        ProductEntity product = baseProduct().suspensionStatus(DomainStatus.PRODUCT_SUSPENSION_DEFAULTER).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("defaulting subscriber");
    }

    @Test
    void validate_immediate_incompatibleProductStatus_throwsBadRequest() {
        ProductEntity product = baseProduct().status(DomainStatus.PRODUCT_CANCELLED).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("immediate cancellation is only allowed");
    }

    @Test
    void validate_immediate_incompatibleCancellationStatus_throwsBadRequest() {
        ProductEntity product = baseProduct().cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_CANCELLED).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("immediate cancellation is only allowed");
    }

    @Test
    void validate_immediate_oneShotWithoutService_noLongerRequiresRefundOfExistingBills() {
        // Decisão revista (ver decisoes.md): este serviço não impõe mais que
        // TODA fatura de um produto ONESHOT sem serviço apareça em `refund`.
        // Quais faturas são estornadas é decisão de quem chama o serviço -
        // um cancelamento IMMEDIATE sem hasRefund algum deve passar normalmente,
        // mesmo havendo uma fatura existente para o produto (o `@BeforeEach`
        // já deixa `billRepository` neutro/vazio por padrão, nem chegando a
        // ser consultado aqui, já que hasRefund=false).
        ProductEntity product = baseProduct().type("ONESHOT").expirationService(false).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatCode(() -> service.validate(request(CancellationType.IMMEDIATE, false, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_immediate_oneShotWithoutService_partialRefundOfOneBillIsAllowed() {
        // Estorno parcial (ou de só uma entre várias faturas) não é mais
        // barrado por esta regra - só as regras 1 a 3 de estorno em si
        // (paga, dentro do saldo estornável) continuam se aplicando.
        ProductEntity product = baseProduct().type("ONESHOT").expirationService(false).build();
        BillEntity bill = BillEntity.builder().id(9L).productId(1L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO)
                .balanceValue(BigDecimal.ZERO).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(bill));
        when(billRepository.findById(9L)).thenReturn(Optional.of(bill));

        RefundRequestItem partialRefund = new RefundRequestItem("BILL_9", BigDecimal.ONE);

        ValidatedCancellationContext context = service.validate(request(CancellationType.IMMEDIATE, true, List.of(partialRefund)));

        assertThat(context.refunds()).hasSize(1);
        assertThat(context.refunds().getFirst().amount()).isEqualByComparingTo(BigDecimal.ONE);
    }

    // ------------------------------------------------------------------
    // SCHEDULED
    // ------------------------------------------------------------------

    @Test
    void validate_scheduled_productWithoutService_throwsBadRequest() {
        ProductEntity product = baseProduct().expirationService(false).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.SCHEDULED, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    void validate_scheduled_lateTransaction_isDowngradedToImmediate() {
        // transactionDt < cycleStartDt do produto -> processar como IMMEDIATE (Regra 2).
        ProductEntity product = baseProduct().cycleStartDt(TRANSACTION_DT + 1).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ValidatedCancellationContext context = service.validate(request(CancellationType.SCHEDULED, false, null));

        assertThat(context.inputType()).isEqualTo(CancellationType.SCHEDULED);
        assertThat(context.processedType()).isEqualTo(CancellationType.IMMEDIATE);
    }

    @Test
    void validate_scheduled_happyPath_isNotDowngraded() {
        ProductEntity product = baseProduct().build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ValidatedCancellationContext context = service.validate(request(CancellationType.SCHEDULED, false, null));

        assertThat(context.processedType()).isEqualTo(CancellationType.SCHEDULED);
    }

    @Test
    void validate_scheduled_alreadyScheduled_throwsBadRequest() {
        ProductEntity product = baseProduct().cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.SCHEDULED, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("scheduled cancellation is only allowed");
    }

    // ------------------------------------------------------------------
    // WITHDRAW_CANCELLATION
    // ------------------------------------------------------------------

    @Test
    void validate_withdraw_happyPath() {
        ProductEntity product = baseProduct().cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED).autoCancelSch(false).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ValidatedCancellationContext context = service.validate(request(CancellationType.WITHDRAW_CANCELLATION, false, null));

        assertThat(context.processedType()).isEqualTo(CancellationType.WITHDRAW_CANCELLATION);
    }

    @Test
    void validate_withdraw_notScheduled_throwsBadRequest() {
        ProductEntity product = baseProduct().cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.WITHDRAW_CANCELLATION, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("withdrawing a cancellation");
    }

    @Test
    void validate_withdraw_autoScheduled_throwsBadRequest() {
        ProductEntity product = baseProduct().cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_SCHEDULED).autoCancelSch(true).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.validate(request(CancellationType.WITHDRAW_CANCELLATION, false, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("withdrawing a cancellation");
    }

    // ------------------------------------------------------------------
    // Refund
    // ------------------------------------------------------------------

    @Test
    void validate_refundRequested_butNoBillsAssociated_throwsBadRequest() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, true, List.of(new RefundRequestItem("BILL_9", BigDecimal.TEN)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no bills associated");
    }

    @Test
    void validate_refundRequested_withEmptyRefundList_throwsBadRequest() {
        BillEntity bill = BillEntity.builder().id(9L).productId(1L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO)
                .balanceValue(BigDecimal.ZERO).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(bill));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, true, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one item");
    }

    @Test
    void validate_refundItem_billNotFound_throwsNotFound() {
        BillEntity anyBill = BillEntity.builder().id(1L).productId(1L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO)
                .balanceValue(BigDecimal.ZERO).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(anyBill));
        when(billRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, true, List.of(new RefundRequestItem("BILL_99", BigDecimal.TEN)))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void validate_refundItem_billBelongsToAnotherProduct_throwsBadRequest() {
        BillEntity bill = BillEntity.builder().id(9L).productId(2L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO)
                .balanceValue(BigDecimal.ZERO).build();
        ProductEntity anyBillForListing = baseProduct().build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(anyBillForListing));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(bill));
        when(billRepository.findById(9L)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, true, List.of(new RefundRequestItem("BILL_9", BigDecimal.TEN)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong to the informed productId");
    }

    @Test
    void validate_refundItem_zeroOrNegativeAmount_throwsBadRequest() {
        BillEntity bill = BillEntity.builder().id(9L).productId(1L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO)
                .balanceValue(BigDecimal.ZERO).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(bill));
        when(billRepository.findById(9L)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, true, List.of(new RefundRequestItem("BILL_9", BigDecimal.ZERO)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void validate_refundItem_billNotFullyPaid_throwsBadRequest() {
        BillEntity bill = BillEntity.builder().id(9L).productId(1L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO)
                .balanceValue(BigDecimal.ONE).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(bill));
        when(billRepository.findById(9L)).thenReturn(Optional.of(bill));

        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, true, List.of(new RefundRequestItem("BILL_9", BigDecimal.ONE)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not fully paid");
    }

    @Test
    void validate_refundItem_amountExceedsRefundableBalance_throwsBadRequest() {
        BillEntity bill = BillEntity.builder().id(9L).productId(1L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.valueOf(8))
                .balanceValue(BigDecimal.ZERO).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(bill));
        when(billRepository.findById(9L)).thenReturn(Optional.of(bill));

        // saldo estornável = 10 - 8 = 2; pedir 3 deve falhar.
        assertThatThrownBy(() -> service.validate(request(CancellationType.IMMEDIATE, true, List.of(new RefundRequestItem("BILL_9", BigDecimal.valueOf(3))))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed the bill's refundable balance");
    }

    @Test
    void validate_refundItem_happyPath_resolvesTheBillAndAmount() {
        BillEntity bill = BillEntity.builder().id(9L).productId(1L).chargedValue(BigDecimal.TEN).refundValue(BigDecimal.ZERO)
                .balanceValue(BigDecimal.ZERO).build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(1L))).thenReturn(List.of(bill));
        when(billRepository.findById(9L)).thenReturn(Optional.of(bill));

        ValidatedCancellationContext context = service.validate(
                request(CancellationType.IMMEDIATE, true, List.of(new RefundRequestItem("BILL_9", BigDecimal.valueOf(4)))));

        assertThat(context.refunds()).hasSize(1);
        assertThat(context.refunds().getFirst().bill()).isEqualTo(bill);
        assertThat(context.refunds().getFirst().amount()).isEqualByComparingTo(BigDecimal.valueOf(4));
    }

    @Test
    void validate_hasRefundFalse_ignoresAnyRefundListEvenIfPresent() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(baseProduct().build()));

        ValidatedCancellationContext context = service.validate(
                request(CancellationType.IMMEDIATE, false, List.of(new RefundRequestItem("BILL_9", BigDecimal.TEN))));

        assertThat(context.refunds()).isEmpty();
    }
}
