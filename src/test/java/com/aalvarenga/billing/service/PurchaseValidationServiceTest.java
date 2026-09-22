package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.AddressRequest;
import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.DocumentRequest;
import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.PhoneRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.request.TaxRequest;
import com.aalvarenga.billing.dto.request.TokenRequest;
import com.aalvarenga.billing.entity.LogEntity;
import com.aalvarenga.billing.enums.AddressType;
import com.aalvarenga.billing.enums.CardBrand;
import com.aalvarenga.billing.enums.DocumentType;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.enums.ProductType;
import com.aalvarenga.billing.enums.RecurrenceFrequency;
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
import org.springframework.http.HttpStatus;

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
 *
 * <p><b>Rodada acrescentada em 22/09/2026</b> (varredura de cobertura pedida
 * pelo usuário - ver adendo do documento de decisões): até então, esta era a
 * classe mais complexa do projeto (54 pontos de {@code throw
 * BusinessException} em {@link PurchaseValidationService}) com a cobertura
 * de teste de unidade proporcionalmente mais fina - a maioria das regras de
 * {@code validateDocuments}/{@code validateAddresses}/{@code validatePhones}/
 * {@code validateProducts}/{@code validatePayments}/{@code validateTokens}/
 * {@code validateBillings} só era exercitada via
 * {@code erros-de-validacao.feature} (Cucumber + Testcontainers, ou seja,
 * precisa subir Docker/MySQL). Os métodos abaixo fecham essa lacuna com
 * testes puros em Mockito (sem Spring context, sem banco, rodam em
 * milissegundos com {@code mvn test}) - a suíte Cucumber continua existindo
 * e validando o mesmo terreno pela via HTTP/E2E, mas o feedback do dia a dia
 * (rodar {@code mvn test} sem precisar de Docker) não depende mais só dela
 * para pegar uma regressão nestas regras.
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

    // ------------------------------------------------------------------
    // Dados gerais (channel / transactionDt / protocol / account) -
    // pontos que ainda não tinham teste de unidade próprio (22/09/2026).
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenTransactionDtIsNotANumber() {
        PurchaseRequest request = baseRequestBuilder("not-a-number", List.of());

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("transactionDt must be a valid epoch milliseconds value");
    }

    @Test
    void rejectsWhenAccountListIsEmpty() {
        ProductRequest product = validOneShotProduct();
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null);

        PurchaseRequest request = new PurchaseRequest(
                "WEB", String.valueOf(pastEpoch()), "WEB-12345", List.of(), List.of(product), List.of(payment), List.of());

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Exactly one account must be informed");
    }

    @Test
    void rejectsWhenProtocolAlreadySucceeded() {
        AccountRequest account = new AccountRequest("123", null, null, null, null, null, "test@example.com", true);
        ProductRequest product = validOneShotProduct();
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null);

        LogEntity successLog = LogEntity.builder().protocol("WEB-12345").result("SUCCESS").build();
        when(logRepository.findByProtocol("WEB-12345")).thenReturn(List.of(successLog));

        PurchaseRequest request = new PurchaseRequest(
                "WEB", String.valueOf(pastEpoch()), "WEB-12345", List.of(account), List.of(product), List.of(payment), List.of());

        // Diferente do resto da suíte, este teste NÃO passa por
        // baseRequestBuilder/requestWithAccount (que já registram um stub
        // leniente para findByProtocol(anyString())) de propósito: como o
        // Mockito prioriza o ÚLTIMO "when" registrado para uma mesma
        // invocação, montar a requisição manualmente aqui garante que o
        // stub específico acima (protocolo exato -> log de SUCESSO) é
        // realmente o que responde a esta chamada.
        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsWhenAccountIdIsNotNumeric() {
        AccountRequest account = new AccountRequest("abc", null, null, null, null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.id must be a valid numeric id");
    }

    @Test
    void rejectsWhenAccountIdIsNotFound() {
        AccountRequest account = new AccountRequest("999", null, null, null, null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);
        // "999" nunca é stubado em nenhum dos "when(accountRepository...)"
        // desta classe - o Mockito já devolve Optional.empty() por padrão
        // para uma invocação não configurada, exatamente o cenário real de
        // um account.id que não existe no banco.

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsWhenNewAccountNameIsBlank() {
        AccountRequest account = new AccountRequest(
                null, "   ", "EXT-1", List.of(validDocument()), null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.name is required when account.id is null");
    }

    @Test
    void rejectsWhenNewAccountExternalIdIsBlank() {
        AccountRequest account = new AccountRequest(
                null, "Angelo Alvarenga", "   ", List.of(validDocument()), null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.externalId is required when account.id is null");
    }

    @Test
    void rejectsWhenNewAccountDocumentIsMissing() {
        AccountRequest account = new AccountRequest(
                null, "Angelo Alvarenga", "EXT-1", null, null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.document is required when account.id is null");
    }

    @Test
    void rejectsWhenExternalIdBelongsToAnotherAccount() {
        AccountRequest account = new AccountRequest(
                null, "Angelo Alvarenga", "EXT-1", List.of(validDocument()), null, null, "test@example.com", true);
        // "existingAccount" é null (conta nova) - QUALQUER conta já dona
        // deste externalId gera conflito, mesmo com um id diferente.
        when(accountRepository.findByExternalId("EXT-1")).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(999L).name("Outra conta").externalId("EXT-1").status(1).build()));
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // account.document / account.address / account.phone (22/09/2026) -
    // até então só cobertas via erros-de-validacao.feature (Cucumber).
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenDocumentTypeIsNull() {
        DocumentRequest document = new DocumentRequest(null, null, "12345678900", "BR");
        AccountRequest account = new AccountRequest(
                "123", null, null, List.of(document), null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.document.type is required");
    }

    @Test
    void rejectsWhenDocumentValueIsBlank() {
        DocumentRequest document = new DocumentRequest(DocumentType.CPF, null, "   ", "BR");
        AccountRequest account = new AccountRequest(
                "123", null, null, List.of(document), null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.document.value is required");
    }

    @Test
    void rejectsWhenDocumentCountryIsBlank() {
        DocumentRequest document = new DocumentRequest(DocumentType.CPF, null, "12345678900", "   ");
        AccountRequest account = new AccountRequest(
                "123", null, null, List.of(document), null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.document.country is required");
    }

    @Test
    void rejectsWhenDocumentCountryIsUnknown() {
        when(countryDomainRepository.existsById("ZZ")).thenReturn(false);
        DocumentRequest document = new DocumentRequest(DocumentType.CPF, null, "12345678900", "ZZ");
        AccountRequest account = new AccountRequest(
                "123", null, null, List.of(document), null, null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown country code: ZZ");
    }

    @Test
    void rejectsWhenAddressTypeIsNull() {
        AddressRequest address = new AddressRequest(null, "Casa", "Rua Um", "100", null, "22000000", "BR");
        AccountRequest account = new AccountRequest(
                "123", null, null, null, List.of(address), null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.address.type is required");
    }

    @Test
    void rejectsWhenAddressNameIsBlank() {
        AddressRequest address = new AddressRequest(
                AddressType.RESIDENCIAL, "Casa", "   ", "100", null, "22000000", "BR");
        AccountRequest account = new AccountRequest(
                "123", null, null, null, List.of(address), null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.address.addressName is required");
    }

    @Test
    void rejectsWhenAddressCountryIsUnknown() {
        when(countryDomainRepository.existsById("ZZ")).thenReturn(false);
        AddressRequest address = new AddressRequest(
                AddressType.RESIDENCIAL, "Casa", "Rua Um", "100", null, "22000000", "ZZ");
        AccountRequest account = new AccountRequest(
                "123", null, null, null, List.of(address), null, "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown country code: ZZ");
    }

    @Test
    void rejectsWhenPhoneNumberIsBlank() {
        PhoneRequest phone = new PhoneRequest("   ");
        AccountRequest account = new AccountRequest(
                "123", null, null, null, null, List.of(phone), "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.phone.number is required and must contain digits only");
    }

    @Test
    void rejectsWhenPhoneNumberContainsNonDigits() {
        PhoneRequest phone = new PhoneRequest("5521-9999-9999");
        AccountRequest account = new AccountRequest(
                "123", null, null, null, null, List.of(phone), "test@example.com", true);
        PurchaseRequest request = requestWithAccount(account);

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("account.phone.number is required and must contain digits only");
    }

    // ------------------------------------------------------------------
    // Produtos (22/09/2026) - até então só cobertas via
    // erros-de-validacao.feature (Cucumber).
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenProductCodeIdIsDuplicated() {
        ProductRequest first = validOneShotProduct();
        ProductRequest duplicate = new ProductRequest(
                "1", "Other product", ProductType.ONESHOT, true, null,
                new BigDecimal("5.00"), BigDecimal.ZERO, null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(first, duplicate));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.codeId must be unique within the request");
    }

    @Test
    void rejectsWhenProductTypeIsNull() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", null, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.type is required");
    }

    @Test
    void rejectsWhenIsExpiriationServiceIsNull() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, null, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.isExpiriationService is required");
    }

    @Test
    void rejectsWhenRecurrenceProductHasIsExpiriationServiceFalse() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.RECURRENCE, false, RecurrenceFrequency.MONTH,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("isExpiriationService cannot be false when product.type is RECURRENCE");
    }

    @Test
    void rejectsWhenRecurrenceProductIsMissingRecurrenceFrequency() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.RECURRENCE, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.recurrenceFrequency is required when product.type is RECURRENCE");
    }

    @Test
    void rejectsWhenProductValueIsNegative() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, true, null,
                new BigDecimal("-1.00"), BigDecimal.ZERO, null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.productValue is required and must not be negative");
    }

    @Test
    void rejectsWhenDiscountValueIsNegative() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), new BigDecimal("-1.00"), null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.discountValue is required and must not be negative");
    }

    @Test
    void rejectsWhenDiscountValueIsGreaterThanProductValue() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), new BigDecimal("20.00"), null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.discountValue cannot be greater than product.productValue");
    }

    @Test
    void rejectsWhenDiscountCyclesIsMissingForADiscountedProduct() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), new BigDecimal("5.00"), null, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.discountCycles is required and must not be negative");
    }

    @Test
    void rejectsWhenRecurrenceProductWithDiscountHasZeroDiscountCycles() {
        // "O valor mínimo é 1 para os descontos na modalidade RECURRENCE"
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.RECURRENCE, true, RecurrenceFrequency.MONTH,
                new BigDecimal("10.00"), new BigDecimal("5.00"), 0, "BRL", false, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.discountCycles must be at least 1 for RECURRENCE products with a discount");
    }

    @Test
    void rejectsWhenTrialProductIsMissingTrialDays() {
        ProductRequest product = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", true, null);
        PurchaseRequest request = requestWithProducts(List.of(product));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("product.trialDays is required and must be greater than zero");
    }

    // ------------------------------------------------------------------
    // Pagamentos (22/09/2026) - até então só cobertas via
    // erros-de-validacao.feature (Cucumber).
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenPaymentIsRequiredButMissing() {
        // Produto RECURRENCE torna "payment" obrigatório mesmo sem cobrança.
        ProductRequest product = validRecurrenceProduct(RecurrenceFrequency.MONTH);
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PurchaseRequest request = requestWithProductsAndPayments(List.of(product), List.of());

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment is required when there is a charge or a RECURRENCE product");
    }

    @Test
    void rejectsWhenPaymentMethodIsNull() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(null, null, null, null, null, true, 1, null, null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.method is required");
    }

    @Test
    void rejectsWhenCreditPaymentIsMissingCardNumber() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(
                PaymentMethod.CREDIT, "Santander", null, "12/79", true, true, 1, null, CardBrand.VISA);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.cardNumber is required");
    }

    @Test
    void rejectsWhenCreditPaymentIsMissingExpiration() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(
                PaymentMethod.CREDIT, "Santander", "1111222233334444", null, true, true, 1, null, CardBrand.VISA);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.expiration is required");
    }

    @Test
    void rejectsWhenCreditPaymentIsMissingIsMultiple() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(
                PaymentMethod.CREDIT, "Santander", "1111222233334444", "12/79", null, true, 1, null, CardBrand.VISA);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.isMultiple is required for CREDIT/DEBIT methods");
    }

    @Test
    void rejectsWhenPaymentIsDefaultIsNull() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, null, 1, null, null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.isDefault is required");
    }

    @Test
    void rejectsWhenNoPaymentHasIsDefaultTrue() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, false, 1, null, null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exactly one payment method must have isDefault = true");
    }

    @Test
    void rejectsWhenMultiplePaymentsHaveIsDefaultTrue() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest first = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null);
        PaymentRequest second = new PaymentRequest(PaymentMethod.WALLET, null, null, null, null, true, 1, null, null);
        PurchaseRequest request = requestWithPayments(List.of(first, second));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exactly one payment method must have isDefault = true");
    }

    @Test
    void rejectsWhenInstallmentsIsZero() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 0, null, null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("installments is required and must be greater than zero");
    }

    @Test
    void rejectsWhenPixPaymentHasMoreThanOneInstallment() {
        // PIX não permite parcelamento (PaymentMethod.allowsInstallments() = false).
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 2, null, null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("installments must be 1 for method PIX");
    }

    @Test
    void rejectsWhenCreditPaymentHasMoreThan12Installments() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        PaymentRequest payment = new PaymentRequest(
                PaymentMethod.CREDIT, "Santander", "1111222233334444", "12/79", true, true, 13, null, CardBrand.VISA);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("installments must be between 1 and 12 for method CREDIT");
    }

    @Test
    void rejectsWhenTokenNameIsBlank() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        TokenRequest token = new TokenRequest("   ", "abc-123", "INTERNAL", null);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, List.of(token), null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.token.name is required");
    }

    @Test
    void rejectsWhenTokenIdIsBlank() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        TokenRequest token = new TokenRequest("ACCESS_TOKEN", "   ", "INTERNAL", null);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, List.of(token), null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.token.id is required");
    }

    @Test
    void rejectsWhenTokenGatewayIsBlank() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        TokenRequest token = new TokenRequest("ACCESS_TOKEN", "abc-123", "   ", null);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, List.of(token), null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payment.token.gateway is required");
    }

    @Test
    void rejectsWhenTokenIdIsAlreadyRegistered() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        when(paymentTokenRepository.existsByToken("abc-123")).thenReturn(true);
        TokenRequest token = new TokenRequest("ACCESS_TOKEN", "abc-123", "INTERNAL", null);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, List.of(token), null);
        PurchaseRequest request = requestWithPayments(List.of(payment));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Faturamento / billing (22/09/2026) - até então só cobertas via
    // erros-de-validacao.feature (Cucumber).
    // ------------------------------------------------------------------

    @Test
    void rejectsWhenBillingCodeIdDoesNotMatchAnyProduct() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        BillingRequest billing = new BillingRequest(
                "999", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX, List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));
        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("billing.codeId does not match any product.codeId");
    }

    @Test
    void rejectsWhenBillingProductValueIsNegative() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("-1.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX, List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));
        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("billing.productValue is required and must not be negative");
    }

    @Test
    void rejectsWhenBillingCurrencyIsUnknown() {
        when(currencyDomainRepository.existsById("BRL")).thenReturn(true);
        when(currencyDomainRepository.existsById("XXX")).thenReturn(false);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "XXX", "TX-1", 1, "CIELO", PaymentMethod.PIX, List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));
        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unknown currency code: XXX");
    }

    @Test
    void rejectsWhenBillingTransactionIdIsAlreadyRegistered() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        when(billRepository.existsByTransactionId("TX-1")).thenReturn(true);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX, List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));
        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsWhenBillingProviderIsBlank() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "   ", PaymentMethod.PIX, List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));
        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("billing.provider is required");
    }

    @Test
    void rejectsWhenBillingPaymentMethodIsNull() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", null, List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));
        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("billing.paymentMethod is required");
    }

    @Test
    void rejectsWhenBillingPaymentMethodDoesNotMatchAnyInformedPayment() {
        // baseRequestBuilder já monta a compra com um payment PIX - uma
        // fatura pedindo CREDIT não bate com nenhum payment.method informado.
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("2.00"), new BigDecimal("12.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.CREDIT, List.of(new TaxRequest("CBS", new BigDecimal("2.00"))));
        PurchaseRequest request = baseRequestBuilder(String.valueOf(pastEpoch()), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("billing.paymentMethod does not match any informed payment.method: CREDIT");
    }

    @Test
    void rejectsWhenMonthlyRecurrenceBillingHasMoreThanOneInstallment() {
        // WALLET permite parcelamento (1-12x) em geral, MAS um produto
        // RECURRENCE com recurrenceFrequency=MONTH força installments=1
        // mesmo para métodos que normalmente aceitariam mais parcelas.
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        ProductRequest product = validRecurrenceProduct(RecurrenceFrequency.MONTH);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.WALLET, null, null, null, null, true, 1, null, null);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("10.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"),
                "BRL", "TX-1", 2, "CIELO", PaymentMethod.WALLET, null);
        PurchaseRequest request = requestWithProductsAndPayments(List.of(product), List.of(payment), List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("installments must be 1 for method WALLET with a MONTH recurrence product");
    }

    @Test
    void rejectsWhenTrialProductBillingChargedValueIsNotZero() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        ProductRequest trialProduct = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, true, null,
                new BigDecimal("5.00"), BigDecimal.ZERO, null, "BRL", true, 7);
        BillingRequest billing = new BillingRequest(
                "1", new BigDecimal("5.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00"),
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX, null);
        PurchaseRequest request = requestWithProductsAndPayments(
                List.of(trialProduct),
                List.of(new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null)),
                List.of(billing));

        assertThatThrownBy(() -> service.validate(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("billing.chargedValue must be zero for a trial product");
    }

    @Test
    void acceptsATrialProductBillingWithZeroChargedValue() {
        when(currencyDomainRepository.existsById(anyString())).thenReturn(true);
        ProductRequest trialProduct = new ProductRequest(
                "1", "Test Product", ProductType.ONESHOT, true, null,
                BigDecimal.ZERO, BigDecimal.ZERO, null, "BRL", true, 7);
        BillingRequest billing = new BillingRequest(
                "1", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "BRL", "TX-1", 1, "CIELO", PaymentMethod.PIX, null);
        PurchaseRequest request = requestWithProductsAndPayments(
                List.of(trialProduct),
                List.of(new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null)),
                List.of(billing));

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

    // ------------------------------------------------------------------
    // Helpers adicionados em 22/09/2026 para fechar a lacuna de cobertura de
    // PurchaseValidationService (ver javadoc da classe) - produtos/documentos
    // "válidos por padrão" para tests que querem isolar uma única regra, e
    // construtores de PurchaseRequest focados em produto/pagamento/fatura.
    // ------------------------------------------------------------------

    private ProductRequest validOneShotProduct() {
        return new ProductRequest("1", "Test Product", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
    }

    private ProductRequest validRecurrenceProduct(RecurrenceFrequency frequency) {
        return new ProductRequest("1", "Test Product", ProductType.RECURRENCE, true, frequency,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);
    }

    private DocumentRequest validDocument() {
        return new DocumentRequest(DocumentType.CPF, null, "12345678900", "BR");
    }

    private PurchaseRequest requestWithProducts(List<ProductRequest> products) {
        AccountRequest account = new AccountRequest(
                "123", null, null, null, null, null, "test@example.com", true);
        PaymentRequest payment = new PaymentRequest(PaymentMethod.PIX, null, null, null, null, true, 1, null, null);

        lenient().when(accountRepository.findById(123L)).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(123L).name("x").externalId("ext-1").status(1).build()));
        lenient().when(logRepository.findByProtocol(anyString())).thenReturn(List.of());
        lenient().when(currencyDomainRepository.existsById(anyString())).thenReturn(true);

        return new PurchaseRequest("WEB", String.valueOf(pastEpoch()), "WEB-12345", List.of(account), products, List.of(payment), List.of());
    }

    private PurchaseRequest requestWithPayments(List<PaymentRequest> payments) {
        AccountRequest account = new AccountRequest(
                "123", null, null, null, null, null, "test@example.com", true);
        ProductRequest product = new ProductRequest(
                "1", "Test Streaming 1", ProductType.ONESHOT, true, null,
                new BigDecimal("10.00"), BigDecimal.ZERO, null, "BRL", false, null);

        lenient().when(accountRepository.findById(123L)).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(123L).name("x").externalId("ext-1").status(1).build()));
        lenient().when(logRepository.findByProtocol(anyString())).thenReturn(List.of());

        return new PurchaseRequest("WEB", String.valueOf(pastEpoch()), "WEB-12345", List.of(account), List.of(product), payments, List.of());
    }

    private PurchaseRequest requestWithProductsAndPayments(List<ProductRequest> products, List<PaymentRequest> payments) {
        return requestWithProductsAndPayments(products, payments, List.of());
    }

    private PurchaseRequest requestWithProductsAndPayments(
            List<ProductRequest> products, List<PaymentRequest> payments, List<BillingRequest> billings) {
        AccountRequest account = new AccountRequest(
                "123", null, null, null, null, null, "test@example.com", true);

        lenient().when(accountRepository.findById(123L)).thenReturn(java.util.Optional.of(
                com.aalvarenga.billing.entity.AccountEntity.builder().id(123L).name("x").externalId("ext-1").status(1).build()));
        lenient().when(logRepository.findByProtocol(anyString())).thenReturn(List.of());

        return new PurchaseRequest("WEB", String.valueOf(pastEpoch()), "WEB-12345", List.of(account), products, payments, billings);
    }
}
