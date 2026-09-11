package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.request.TaxRequest;
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

    private PurchaseValidationService service;

    @BeforeEach
    void setUp() {
        service = new PurchaseValidationService(
                accountRepository, logRepository, countryDomainRepository,
                currencyDomainRepository, billRepository, paymentTokenRepository);
    }

    @Test
    void rejectsTransactionDateInTheFuture() {
        PurchaseRequest request = baseRequestBuilder(String.valueOf(System.currentTimeMillis() + 60_000), List.of());

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("transactionDate cannot be in the future");
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
    private PurchaseRequest baseRequestBuilder(String transactionDate, List<BillingRequest> billing) {
        AccountRequest account = new AccountRequest("123", null, null, null, null, null);
        ProductRequest product = new ProductRequest(
                "1", "Test Streaming 1", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);

        // Pagamento PIX "neutro", só para satisfazer a regra "payment é obrigatório
        // quando há cobrança efetiva" nos testes focados em billing - não é o alvo
        // destes testes, mas precisa existir para a validação chegar até o billing.
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null);

        // "lenient" porque nem todo teste chega a exercitar estas consultas (ex: o
        // teste que valida transactionDate no futuro falha ANTES de chegar aqui).
        lenient().when(accountRepository.findById(123L)).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(123L).name("x").externalId("ext-1").status(1).build()));
        lenient().when(logRepository.findByProtocol(anyString())).thenReturn(List.of());

        return new PurchaseRequest("WEB", transactionDate, "WEB-12345", List.of(account), List.of(product), List.of(payment), billing);
    }
}
