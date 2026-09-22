package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PurchaseQueryRequest;
import com.aalvarenga.billing.dto.response.QueryAccountItem;
import com.aalvarenga.billing.dto.response.QueryBillItem;
import com.aalvarenga.billing.dto.response.QueryDiscountItem;
import com.aalvarenga.billing.dto.response.QueryPaymentItem;
import com.aalvarenga.billing.dto.response.QueryProductItem;
import com.aalvarenga.billing.dto.response.QueryResponse;
import com.aalvarenga.billing.entity.AccountDocumentEntity;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.AccountPhoneEntity;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.DiscountEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.ResultStatus;
import com.aalvarenga.billing.exception.BusinessException;
import com.aalvarenga.billing.repository.AccountDocumentRepository;
import com.aalvarenga.billing.repository.AccountPhoneRepository;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.BillTaxRepository;
import com.aalvarenga.billing.repository.DiscountRepository;
import com.aalvarenga.billing.repository.PaymentRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cobre {@link PurchaseQueryService} - a orquestração da consulta de dados
 * ({@code POST /api/v1/purchases/query}). Foco nas regras com cálculo/regra
 * de negócio real (mascaramento, filtro de desconto vigente x
 * {@code nextBillValue}, escopo por produto, limite de faturas, trial ativo)
 * - não em "todo campo foi copiado", que tem menos valor de teste.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseQueryServiceTest {

    @Mock
    private PurchaseQueryValidationService purchaseQueryValidationService;
    @Mock
    private AccountDocumentRepository accountDocumentRepository;
    @Mock
    private AccountPhoneRepository accountPhoneRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private DiscountRepository discountRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private BillTaxRepository billTaxRepository;
    @Mock
    private DomainStatusLookupService domainStatusLookupService;
    @Mock
    private RequestLogService requestLogService;
    @Mock
    private JsonMapper objectMapper;

    private PurchaseQueryService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseQueryService(
                purchaseQueryValidationService, accountDocumentRepository, accountPhoneRepository,
                productRepository, discountRepository, paymentRepository, billRepository, billTaxRepository,
                domainStatusLookupService,
                // Usamos instâncias REAIS destes dois (não mocks): são serviços
                // puros, sem dependência de banco, e usá-los de verdade aqui
                // também exercita a integração real com o cálculo de trial e
                // de parcela, em vez de só verificar que "algum método foi
                // chamado".
                new RecurrenceCalculatorService(), new InstallmentSplitService(),
                requestLogService, objectMapper);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    private AccountEntity account() {
        return AccountEntity.builder()
                .id(1L).externalId("EXT-1").createdDt(1_000L)
                .email("a@a.com").status(1).authorizedFallback(true)
                .build();
    }

    // "accountId" não é parâmetro - IntelliJ aponta "Value of parameter
    // 'accountId' is always '1L'" porque todo teste desta classe usa a
    // mesma conta (id 1L, ver account() acima). Diferente do padrão
    // "generalização deliberada" mantido em AssetIdFormatter/
    // PurchaseValidationService (ver doc de decisões), aqui não há
    // benefício de reaproveitamento futuro - é só um helper de teste local
    // - então o parâmetro foi removido em vez de mantido.
    private ProductEntity product(long id) {
        return ProductEntity.builder()
                .id(id).accountId(1L).productId("1").name("Test Streaming")
                .type("RECURRENCE").expirationService(true).recurrenceFrequency("MONTH")
                .value(new BigDecimal("10.00")).currency("BRL").trial(false)
                .assetId("1").cycleStartDt(1_000L).cycleEndDt(2_000L)
                .defaultPaymentId("3").channel("WEB").transactionDt(1_000L).status(1)
                .disableBilling(false)
                // Campos acrescentados em 21/09/2026 (ver README, seção
                // "Evoluções pedidas") - "cenário 1" (sem cancelamento
                // agendado): DomainStatus.PRODUCT_SUSPENSION_COMPLIANT/
                // PRODUCT_CANCELLATION_NO_SCHEDULES são, coincidentemente,
                // ambos "1".
                .suspensionStatus(DomainStatus.PRODUCT_SUSPENSION_COMPLIANT)
                .cancellationStatus(DomainStatus.PRODUCT_CANCELLATION_NO_SCHEDULES)
                .autoCancelSch(false)
                .build();
    }

    // ------------------------------------------------------------------
    // Caminho feliz - externalId
    // ------------------------------------------------------------------

    @Test
    void successByExternalId_masksSensitiveFieldsAndFormatsIds() {
        AccountEntity account = account();
        ProductEntity product = product(2L);
        product.setNextBillDt(null); // simplifica: sem próxima cobrança neste teste

        ValidatedQueryContext context = new ValidatedQueryContext(account, null, true, true, true, 0);
        when(purchaseQueryValidationService.validate(any())).thenReturn(context);
        when(domainStatusLookupService.accountStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        when(accountDocumentRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of(
                AccountDocumentEntity.builder().id(10L).accountId(1L).type("CPF").description("")
                        .value("12345678922").country("BR").status(1).build()));
        when(accountPhoneRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of(
                AccountPhoneEntity.builder().id(11L).accountId(1L).number("5521999999999").status(1).build()));

        when(productRepository.findByAccountId(1L)).thenReturn(List.of(product));
        when(domainStatusLookupService.productStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        // Acrescentados em 21/09/2026 (ver README, seção "Evoluções pedidas").
        when(domainStatusLookupService.productSuspensionStatuses(Set.of(1))).thenReturn(Map.of(1, "COMPLIENT"));
        when(domainStatusLookupService.productCancellationStatuses(Set.of(1))).thenReturn(Map.of(1, "NO_SCHEDULES"));
        when(discountRepository.findByProductIdIn(List.of(2L))).thenReturn(List.of());

        PaymentEntity payment = PaymentEntity.builder()
                .id(3L).accountId(1L).method("CREDIT").issuer("Santander")
                .cardNumber("4111111111111111").expiration("03/31")
                .multiple(true).defaultMethod(true).installments("1").status(1)
                .brand("VISA")
                .build();
        when(paymentRepository.findByAccountIdAndStatus(1L, DomainStatus.PAYMENT_ACTIVE)).thenReturn(List.of(payment));
        when(domainStatusLookupService.paymentStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));

        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(2L))).thenReturn(List.of());

        ResponseEntity<QueryResponse> responseEntity = service.process(new PurchaseQueryRequest("EXT-1", null, null, null, null, null));

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueryResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        assertThat(response.result()).isEqualTo(ResultStatus.SUCCESS);

        assertThat(response.account()).hasSize(1);
        QueryAccountItem accountItem = response.account().getFirst();
        assertThat(accountItem.accountId()).isEqualTo("ACCT_1");
        assertThat(accountItem.document().getFirst().value()).isEqualTo("*********22");
        assertThat(accountItem.phone().getFirst().number()).isEqualTo("5521999999999");

        assertThat(response.products()).hasSize(1);
        QueryProductItem productItem = response.products().getFirst();
        assertThat(productItem.productId()).isEqualTo("PROD_2");
        assertThat(productItem.defaultPaymentId()).isEqualTo("PAY_3");
        assertThat(productItem.productSt()).isEqualTo("ACTIVE");
        assertThat(productItem.discount()).isNull();

        assertThat(response.payment()).hasSize(1);
        QueryPaymentItem paymentItem = response.payment().getFirst();
        assertThat(paymentItem.paymentId()).isEqualTo("PAY_3");
        // 4 últimos dígitos visíveis (decisão do usuário - ver doc de decisões do projeto).
        assertThat(paymentItem.cardNumber()).isEqualTo("************1111");
        assertThat(paymentItem.installments()).isEqualTo(1);
        // Campo acrescentado em 21/09/2026 (ver decisoes.md): simples repasse
        // de T_PAYMENT.BRAND, sem máscara nem transformação nenhuma.
        assertThat(paymentItem.brand()).isEqualTo("VISA");

        assertThat(response.bill()).isNull();

        verify(requestLogService).log(anyString(), eq("SUCCESS"), eq("200"), eq("Success"), anyString(), anyString());
    }

    // ------------------------------------------------------------------
    // Escopo por productId
    // ------------------------------------------------------------------

    @Test
    void successByProductId_scopesEverythingToThatSingleProduct() {
        AccountEntity account = account();
        ProductEntity product = product(2L);
        product.setNextBillDt(null);

        ValidatedQueryContext context = new ValidatedQueryContext(account, product, false, true, false, 0);
        when(purchaseQueryValidationService.validate(any())).thenReturn(context);
        when(domainStatusLookupService.accountStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        when(accountDocumentRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());
        when(accountPhoneRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());

        PaymentEntity defaultPayment = PaymentEntity.builder()
                .id(3L).accountId(1L).method("CREDIT").installments("1").status(1).build();
        when(paymentRepository.findById(3L)).thenReturn(java.util.Optional.of(defaultPayment));
        when(domainStatusLookupService.paymentStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));

        ResponseEntity<QueryResponse> responseEntity = service.process(new PurchaseQueryRequest(null, "PROD_2", false, true, false, null));

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        QueryResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        assertThat(response.result()).isEqualTo(ResultStatus.SUCCESS);
        // returnProductData=false e returnBillData=false: os repositórios
        // "caros" de produto/fatura não podem ter sido chamados.
        verifyNoInteractions(discountRepository, billRepository, billTaxRepository);
        // Escopado por productId: a busca de pagamentos da CONTA INTEIRA
        // nunca deveria ter sido chamada, só o pagamento padrão do produto.
        verify(paymentRepository, never()).findByAccountIdAndStatus(any(), any());
        verify(paymentRepository).findById(3L);
    }

    // ------------------------------------------------------------------
    // Desconto "vigente" (lista) x desconto considerado no nextBillValue
    // ------------------------------------------------------------------

    @Test
    void discountList_usesNow_whileNextBillValue_usesNextBillDt() {
        long now = System.currentTimeMillis();
        long nextBillDt = now + 200_000L;

        AccountEntity account = account();
        ProductEntity product = product(2L);
        product.setValue(new BigDecimal("100.00"));
        product.setNextBillDt(nextBillDt);

        DiscountEntity stillValidAtNextBill = DiscountEntity.builder()
                .id(20L).productId(2L).startDt(now - 10_000).endDt(now + 500_000).value(new BigDecimal("10.00")).status(1).build();
        DiscountEntity expiresBeforeNextBill = DiscountEntity.builder()
                .id(21L).productId(2L).startDt(now - 10_000).endDt(now + 100_000).value(new BigDecimal("5.00")).status(1).build();
        DiscountEntity alreadyExpired = DiscountEntity.builder()
                .id(22L).productId(2L).startDt(now - 100_000).endDt(now - 1_000).value(new BigDecimal("99.00")).status(1).build();
        DiscountEntity cancelled = DiscountEntity.builder()
                .id(23L).productId(2L).startDt(now - 10_000).endDt(now + 500_000).value(new BigDecimal("50.00")).status(3).build();

        ValidatedQueryContext context = new ValidatedQueryContext(account, product, true, false, false, 0);
        when(purchaseQueryValidationService.validate(any())).thenReturn(context);
        when(domainStatusLookupService.accountStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        when(accountDocumentRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());
        when(accountPhoneRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());
        when(domainStatusLookupService.productStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        // Acrescentados em 21/09/2026 (ver README, seção "Evoluções pedidas").
        when(domainStatusLookupService.productSuspensionStatuses(Set.of(1))).thenReturn(Map.of(1, "COMPLIENT"));
        when(domainStatusLookupService.productCancellationStatuses(Set.of(1))).thenReturn(Map.of(1, "NO_SCHEDULES"));
        when(discountRepository.findByProductIdIn(List.of(2L)))
                .thenReturn(List.of(stillValidAtNextBill, expiresBeforeNextBill, alreadyExpired, cancelled));
        when(domainStatusLookupService.discountStatuses(any())).thenReturn(Map.of(1, "ACTIVE", 3, "CANCELLED"));

        ResponseEntity<QueryResponse> responseEntity = service.process(new PurchaseQueryRequest(null, "PROD_2", true, false, false, null));

        QueryResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        QueryProductItem productItem = response.products().getFirst();
        // Lista de desconto "vigente": os dois com status ativo e endDt > AGORA
        // (exclui o já expirado e o cancelado).
        assertThat(productItem.discount()).extracting(QueryDiscountItem::discountId)
                .containsExactlyInAnyOrder("DISC_20", "DISC_21");
        // nextBillValue: só desconta quem ainda estará vigente na PRÓXIMA
        // cobrança (endDt > nextBillDt) - só o discount 20 se qualifica.
        assertThat(productItem.nextBillValue()).isEqualByComparingTo("90.00");
    }

    // ------------------------------------------------------------------
    // maxBillReturn
    // ------------------------------------------------------------------

    @Test
    void maxBillReturn_limitsHowManyBillsComeBack() {
        AccountEntity account = account();
        ProductEntity product = product(2L);
        product.setNextBillDt(null);

        ValidatedQueryContext context = new ValidatedQueryContext(account, null, false, false, true, 2);
        when(purchaseQueryValidationService.validate(any())).thenReturn(context);
        when(domainStatusLookupService.accountStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        when(accountDocumentRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());
        when(accountPhoneRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());
        when(productRepository.findByAccountId(1L)).thenReturn(List.of(product));

        BillEntity bill1 = bill(101L, 3_000L);
        BillEntity bill2 = bill(102L, 2_000L);
        BillEntity bill3 = bill(103L, 1_000L);
        // O mock já simula o contrato do repositório: ordenado desc por dueDt.
        when(billRepository.findByProductIdInOrderByDueDtDesc(List.of(2L))).thenReturn(List.of(bill1, bill2, bill3));
        when(billTaxRepository.findByBillIdIn(List.of(101L, 102L))).thenReturn(List.of());
        when(domainStatusLookupService.billStatuses(any())).thenReturn(Map.of(4, "PAID"));
        when(domainStatusLookupService.billTypes(any())).thenReturn(Map.of(1, "BUY"));
        when(domainStatusLookupService.billRefundStatuses(any())).thenReturn(Map.of(1, "NO_REFUND"));

        ResponseEntity<QueryResponse> responseEntity = service.process(new PurchaseQueryRequest("EXT-1", null, false, false, true, 2));

        QueryResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        List<QueryBillItem> bills = response.bill();
        assertThat(bills).hasSize(2);
        assertThat(bills).extracting(QueryBillItem::billId).containsExactly("BILL_101", "BILL_102");
    }

    // Mesmo racional do helper product(id) acima: "productId" sempre valia
    // 2L (todas as faturas deste teste pertencem ao mesmo produto) - warning
    // genuíno do IntelliJ, parâmetro removido.
    private BillEntity bill(long id, long dueDt) {
        return BillEntity.builder()
                .id(id).codeId("1").productId(2L).productValue(new BigDecimal("10.00"))
                .discountValue(BigDecimal.ZERO).taxValue(new BigDecimal("2.00")).chargedValue(new BigDecimal("12.00"))
                .currency("BRL").transactionId("TX-" + id).installments("1").provider("CIELO")
                .paymentMethod("CREDIT").status(4).billType(1)
                .cycleStartDt(1_000L).dueDt(dueDt)
                // Campos acrescentados em 21/09/2026 (ver README, seção
                // "Evoluções pedidas"): toda fatura nasce sem saldo em
                // aberto e sem estorno.
                .balanceValue(BigDecimal.ZERO).refundValue(BigDecimal.ZERO)
                .refundStatus(DomainStatus.BILL_REFUND_NO_REFUND)
                .build();
    }

    // ------------------------------------------------------------------
    // Trial ativo x expirado (reaproveita RecurrenceCalculatorService)
    // ------------------------------------------------------------------

    @Test
    void isActiveTrial_trueWhileWithinTrialWindow_falseWhenAlreadyEnded() {
        long now = System.currentTimeMillis();
        AccountEntity account = account();

        ProductEntity ongoingTrial = product(2L);
        ongoingTrial.setTrial(true);
        ongoingTrial.setTrialDays(5);
        ongoingTrial.setTransactionDt(now - java.util.concurrent.TimeUnit.DAYS.toMillis(1)); // comprou ontem, trial de 5 dias -> ainda em trial
        ongoingTrial.setNextBillDt(null);

        ProductEntity endedTrial = product(4L);
        endedTrial.setId(4L);
        endedTrial.setTrial(true);
        endedTrial.setTrialDays(5);
        endedTrial.setTransactionDt(now - java.util.concurrent.TimeUnit.DAYS.toMillis(30)); // comprou há 30 dias -> trial já acabou
        endedTrial.setNextBillDt(null);

        ValidatedQueryContext context = new ValidatedQueryContext(account, null, true, false, false, 0);
        when(purchaseQueryValidationService.validate(any())).thenReturn(context);
        when(domainStatusLookupService.accountStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        when(accountDocumentRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());
        when(accountPhoneRepository.findByAccountIdAndStatus(1L, DomainStatus.ACTIVE)).thenReturn(List.of());
        when(productRepository.findByAccountId(1L)).thenReturn(List.of(ongoingTrial, endedTrial));
        when(domainStatusLookupService.productStatuses(Set.of(1))).thenReturn(Map.of(1, "ACTIVE"));
        // Acrescentados em 21/09/2026 (ver README, seção "Evoluções pedidas").
        when(domainStatusLookupService.productSuspensionStatuses(Set.of(1))).thenReturn(Map.of(1, "COMPLIENT"));
        when(domainStatusLookupService.productCancellationStatuses(Set.of(1))).thenReturn(Map.of(1, "NO_SCHEDULES"));
        when(discountRepository.findByProductIdIn(List.of(2L, 4L))).thenReturn(List.of());

        ResponseEntity<QueryResponse> responseEntity = service.process(new PurchaseQueryRequest("EXT-1", null, true, false, false, null));

        QueryResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        List<QueryProductItem> products = response.products();
        assertThat(products).extracting(QueryProductItem::productId, QueryProductItem::isActiveTrial)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("PROD_2", true),
                        org.assertj.core.groups.Tuple.tuple("PROD_4", false));
    }

    // ------------------------------------------------------------------
    // Erros de validação viram resposta padronizada e são logados
    // ------------------------------------------------------------------

    @Test
    void businessExceptionFromValidation_becomesErrorResponse_andIsStillLogged() {
        when(purchaseQueryValidationService.validate(any()))
                .thenThrow(BusinessException.badRequest("can't find a externalId or productId"));

        ResponseEntity<QueryResponse> responseEntity = service.process(new PurchaseQueryRequest(null, null, null, null, null, null));

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        QueryResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        assertThat(response.result()).isEqualTo(ResultStatus.ERROR);
        assertThat(response.code()).isEqualTo("400");
        assertThat(response.account()).isNull();

        verify(requestLogService).log(anyString(), eq("ERROR"), eq("400"),
                eq("can't find a externalId or productId"), anyString(), anyString());
        verifyNoInteractions(productRepository, paymentRepository, billRepository);
    }
}
