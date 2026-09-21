package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.request.TaxRequest;
import com.aalvarenga.billing.dto.request.TokenRequest;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.enums.ProductType;
import com.aalvarenga.billing.exception.BusinessException;
import com.aalvarenga.billing.repository.AccountRepository;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.CountryDomainRepository;
import com.aalvarenga.billing.repository.CurrencyDomainRepository;
import com.aalvarenga.billing.repository.LogRepository;
import com.aalvarenga.billing.repository.PaymentTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cobre as validações mais críticas de {@link PurchaseValidationService}
 * (as que envolvem cálculo, e não apenas "campo obrigatório"), usando
 * Mockito para simular as consultas ao banco sem precisar de um banco real.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseValidationServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private LogRepository logRepository;
    @Mock
    private CountryDomainRepository countryDomainRepository;
    @Mock
    private CurrencyDomainRepository currencyDomainRepository;
    @Mock
    private BillRepository billRepository;
    @Mock
    private PaymentTokenRepository paymentTokenRepository;
    // Acrescentado em 21/09/2026 (ver README, seção "Evoluções pedidas") -
    // não precisa de stub explícito: sem ele, o Mockito já devolve
    // Optional.empty() por padrão para qualquer método não "stubado" que
    // retorne Optional (comportamento embutido desde o Mockito 2), o que faz
    // a nova checagem de piso mínimo em validateTransactionDt virar um
    // no-op - exatamente o comportamento fail-safe desejado para os testes
    // que não têm relação nenhuma com essa regra.
    @Mock
    private ConfigParameterService configParameterService;

    private PurchaseValidationService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseValidationService(
                accountRepository, logRepository, countryDomainRepository,
                currencyDomainRepository, billRepository, paymentTokenRepository,
                configParameterService);
    }

    @Test
    void rejectsTransactionDtInTheFuture() {
        PurchaseRequest request = baseRequestBuilder(String.valueOf(System.currentTimeMillis() + 60_000), List.of());

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("transactionDt cannot be in the future");
    }

    // ------------------------------------------------------------------
    // Piso mínimo de transactionDt (21/09/2026, ver README, seção
    // "Evoluções pedidas") - T_CONFIG_PARAMETERS.MINIMAL_TRANSACTION_DATE.
    // ------------------------------------------------------------------

    @Test
    void rejectsTransactionDtBeforeConfiguredMinimum() {
        when(configParameterService.getLongValue(ConfigParameterRules.MINIMAL_TRANSACTION_DATE))
                .thenReturn(java.util.Optional.of(1_767_236_400_000L));
        // "1" (1 ms após o epoch) - exatamente o valor de exemplo pedido pelo
        // usuário para este cenário de erro.
        PurchaseRequest request = baseRequestBuilder("1", List.of());

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("minimum accepted date");
    }

    @Test
    void allowsOldTransactionDt_whenMinimumIsNotConfigured() {
        // Nenhum "when(...)" para configParameterService.getLongValue: por
        // padrão do Mockito (desde a v2), um método não "stubado" que
        // devolve Optional já retorna Optional.empty() sozinho - simula
        // exatamente o comportamento fail-safe real (parâmetro ausente =
        // checagem pulada, ver ConfigParameterService/PurchaseValidationService).
        PurchaseRequest request = baseRequestBuilder("1", List.of());

        assertThatThrownBy(() -> service.validate(request))
                // A requisição ainda falha por outro motivo qualquer mais à
                // frente na validação (ex: produto/pagamento incompletos
                // para este teste minimalista) - o ponto aqui é só que ela
                // NÃO falha por causa de transactionDt, provando que a
                // checagem de piso mínimo foi de fato pulada.
                .isInstanceOf(BusinessException.class)
                .hasMessageNotContaining("transactionDt");
    }

    // ------------------------------------------------------------------
    // Campos acrescentados em 17/09/2026 (ver README, seção "Evoluções
    // pedidas em 17/09/2026"): account.email, account.isAuthorizedFallback
    // e payment.brand.
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenAccountEmailIsBlank() {
        PurchaseRequest request = requestWithAccount(
                new AccountRequest("123", null, null, null, null, null, "   ", true));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.email is required");
    }

    @Test
    void rejectsWhenAccountIsAuthorizedFallbackIsNull() {
        PurchaseRequest request = requestWithAccount(
                new AccountRequest("123", null, null, null, null, null, "test@example.com", null));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.isAuthorizedFallback is required");
    }

    @Test
    void rejectsWhenCreditPaymentIsMissingBrand() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);

        // "brand" propositalmente omitido (null) - método CREDIT o exige.
        PaymentRequest creditWithoutBrand = new PaymentRequest(
                PaymentMethod.CREDIT, "Santander", "1111222233334444", "12/79", true, true, 1, null, null);
        PurchaseRequest request = requestWithPayment(creditWithoutBrand);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.brand is required");
    }

    @Test
    void rejectsWhenTaxSumDoesNotMatchTaxValue() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);

        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX,
                List.of(new TaxRequest("CBS", new BigDecimal("0.50")), new TaxRequest("IBS", new BigDecimal("1.00"))));

        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sum of taxes does not match");
    }

    // ------------------------------------------------------------------
    // Campos acrescentados em 19/09/2026 (ver README, seção "Evoluções
    // pedidas"): billing.tax.name (bug corrigido) e payment.token.expirationDt
    // (melhoria - antigo expirationDate).
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenBillingTaxNameIsBlank() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);

        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX,
                List.of(new TaxRequest(null, new BigDecimal("2.00"))));

        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("billing.tax.name is required");
    }

    @Test
    void rejectsWhenTokenExpirationDtIsInThePast() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);

        TokenRequest expiredToken = new TokenRequest("ACCESS_TOKEN", "abc-123", "INTERNAL", "1");
        PaymentRequest payment = new PaymentRequest(
                PaymentMethod.PIX, null, null, null, null, true, 1, List.of(expiredToken), null);
        PurchaseRequest request = requestWithPayment(payment);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expirationDt cannot be in the past");
    }

    @Test
    void rejectsWhenChargedValueFormulaDoesNotMatch() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);

        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("999.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX,
                List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));

        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match chargedValue");
    }

    @Test
    void acceptsAConsistentBilling() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);

        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX,
                List.of(new TaxRequest("CBS", new BigDecimal("0.50")), new TaxRequest("IBS", new BigDecimal("1.50"))));

        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        ValidatedPurchaseContext context = service.validate(request);

        assertThat(context).isNotNull();
    }

    private long pastEpoch() {
        return System.currentTimeMillis() - 60_000;
    }

    /** Monta uma requisição mínima e válida (1 produto RECURRENCE simples), variando apenas a data e o billing. */
    private PurchaseRequest baseRequestBuilder(String transactionDt, List<BillingRequest> billing) {
        AccountRequest account = new AccountRequest(
                "123", null, null, null, null, null, "test@example.com", true);
        ProductRequest product = new ProductRequest(
                "1", "Test Streaming 1", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);

        // Pagamento PIX "neutro", só para satisfazer a regra "payment é obrigatório
        // quando há cobrança efetiva" nos testes focados em billing - não é o alvo
        // destes testes, mas precisa existir para a validação chegar até o billing.
        // "brand" fica null porque PIX não é card-based (não é obrigatório).
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null);

        // "lenient" porque nem todo teste chega a exercitar estas consultas (ex: o
        // teste que valida transactionDt no futuro falha ANTES de chegar aqui).
        lenient().when(accountRepository.findById(123L)).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(123L).name("x").externalId("ext-1").status(1).build()));
        lenient().when(logRepository.findByProtocol(anyString())).thenReturn(List.of());

        return new PurchaseRequest("WEB", transactionDt, "WEB-12345", List.of(account), List.of(product), List.of(payment), billing);
    }

    /** Variante de {@link #baseRequestBuilder} que troca só o "account", mantendo produto/pagamento neutros. */
    private PurchaseRequest requestWithAccount(AccountRequest account) {
        ProductRequest product = new ProductRequest(
                "1", "Test Streaming 1", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null);

        lenient().when(accountRepository.findById(123L)).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(123L).name("x").externalId("ext-1").status(1).build()));
        lenient().when(logRepository.findByProtocol(anyString())).thenReturn(List.of());

        return new PurchaseRequest("WEB", String.valueOf(pastEpoch()), "WEB-12345", List.of(account), List.of(product), List.of(payment), List.of());
    }

    /** Variante de {@link #baseRequestBuilder} que troca só o "payment", mantendo conta/produto neutros. */
    private PurchaseRequest requestWithPayment(PaymentRequest payment) {
        AccountRequest account = new AccountRequest(
                "123", null, null, null, null, null, "test@example.com", true);
        ProductRequest product = new ProductRequest(
                "1", "Test Streaming 1", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);

        lenient().when(accountRepository.findById(123L)).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(123L).name("x").externalId("ext-1").status(1).build()));
        lenient().when(logRepository.findByProtocol(anyString())).thenReturn(List.of());

        return new PurchaseRequest("WEB", String.valueOf(pastEpoch()), "WEB-12345", List.of(account), List.of(product), List.of(payment), List.of());
    }
}
