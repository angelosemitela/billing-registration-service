package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.AccountRequest;
import com.aalvarenga.billing.dto.request.AddressRequest;
import com.aalvarenga.billing.dto.request.BillingRequest;
import com.aalvarenga.billing.dto.request.DocumentRequest;
import com.aalvarenga.billing.dto.request.PaymentRequest;
import com.aalvarenga.billing.dto.request.PhoneRequest;
import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.request.TokenRequest;
import com.aalvarenga.billing.entity.AccountEntity;
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
import com.aalvarenga.billing.util.CardExpirationUtil;
import com.aalvarenga.billing.util.MoneyUtil;
import com.aalvarenga.billing.util.PurchaseLookupUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Concentra TODAS as regras de validação de negócio (cruzadas entre campos e
 * contra o banco de dados) descritas no enunciado do projeto.
 *
 * <p>Optamos por NÃO usar apenas Bean Validation (anotações como
 * {@code @NotNull} nos DTOs) para estas regras porque a maioria delas é
 * CONDICIONAL ("obrigatório somente se..."), envolve MAIS DE UM CAMPO ao
 * mesmo tempo, ou depende de uma CONSULTA AO BANCO (ex: o país existe na
 * tabela de domínio?). Cada regra abaixo referencia, em comentário, a parte
 * do enunciado original que a originou, para facilitar a conferência.
 *
 * <p>Toda vez que uma regra é violada, lançamos {@link BusinessException}
 * com o código HTTP mais adequado dentro da família 4XX:
 * <ul>
 *   <li>{@code 400 Bad Request} - campo obrigatório ausente ou valor fora do domínio/formato;</li>
 *   <li>{@code 404 Not Found} - uma referência informada (ex: account.id) não existe;</li>
 *   <li>{@code 409 Conflict} - conflito com um registro único já existente (protocolo, externalId, transactionId, token);</li>
 *   <li>{@code 412 Precondition Failed} - uma fórmula/somatório de valores não fechou.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PurchaseValidationService {

    private final AccountRepository accountRepository;
    private final LogRepository logRepository;
    private final CountryDomainRepository countryDomainRepository;
    private final CurrencyDomainRepository currencyDomainRepository;
    private final BillRepository billRepository;
    private final PaymentTokenRepository paymentTokenRepository;

    /**
     * Ponto de entrada: valida a requisição inteira, na ordem em que os
     * blocos aparecem no enunciado (dados gerais -> conta -> produtos ->
     * pagamentos -> faturamento), e devolve um pequeno contexto reaproveitado
     * pela persistência (ver {@link ValidatedPurchaseContext}).
     */
    public ValidatedPurchaseContext validate(PurchaseRequest request) {
        long transactionDateEpochMillis = validateTransactionDt(request.transactionDt());
        validateProtocol(request.protocol());

        if (request.account().size() != 1) {
            // O enunciado modela "account" como lista, mas todo o restante da regra de
            // negócio (uma compra = um assinante) só faz sentido para exatamente 1 elemento.
            // Ver README/ANALISE.md, seção "Inconsistências".
            throw BusinessException.badRequest("Exactly one account must be informed");
        }
        AccountEntity existingAccount = validateAccount(request.account().getFirst());

        validateProducts(request.product());

        validatePayments(request.payment(), request.product(), request.billing());

        validateBillings(request.billing(), request.product(), request.payment());

        return new ValidatedPurchaseContext(transactionDateEpochMillis, existingAccount);
    }

    // ------------------------------------------------------------------
    // Dados gerais (channel / transactionDt / protocol)
    // ------------------------------------------------------------------

    private long validateTransactionDt(String transactionDt) {
        long epochMillis;
        try {
            epochMillis = Long.parseLong(transactionDt);
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest("transactionDt must be a valid epoch milliseconds value");
        }
        if (epochMillis > System.currentTimeMillis()) {
            // Regra explícita do enunciado para o campo (antigo "transactionDate",
            // renomeado para "transactionDt" - ver javadoc de PurchaseRequest)
            throw BusinessException.badRequest("transactionDt cannot be in the future");
        }
        return epochMillis;
    }

    private void validateProtocol(String protocol) {
        // Idempotência (decisão registrada em README/ANALISE.md): um protocolo
        // só é considerado "duplicado" se já existir um log de SUCESSO para ele.
        // Um protocolo cujo único histórico é de ERRO pode ser reprocessado.
        boolean alreadySucceeded = logRepository.findByProtocol(protocol).stream()
                .anyMatch(log -> "SUCCESS".equals(log.getResult()));
        if (alreadySucceeded) {
            throw BusinessException.conflict("protocol has already been processed successfully");
        }
    }

    // ------------------------------------------------------------------
    // Conta (account / document / address / phone)
    // ------------------------------------------------------------------

    private AccountEntity validateAccount(AccountRequest accountRequest) {
        boolean isNewAccount = accountRequest.id() == null;

        AccountEntity existingAccount = null;
        if (!isNewAccount) {
            Long accountId = parseId(accountRequest.id(), "account.id");
            existingAccount = accountRepository.findById(accountId)
                    .orElseThrow(() -> BusinessException.notFound("account.id not found"));
        } else {
            // Conta nova: name/externalId/document passam a ser obrigatórios
            if (isBlank(accountRequest.name())) {
                throw BusinessException.badRequest("account.name is required when account.id is null");
            }
            if (isBlank(accountRequest.externalId())) {
                throw BusinessException.badRequest("account.externalId is required when account.id is null");
            }
            if (accountRequest.document() == null || accountRequest.document().isEmpty()) {
                throw BusinessException.badRequest("account.document is required when account.id is null");
            }
        }

        // externalId, quando informado (em conta nova OU em atualização cadastral),
        // não pode já pertencer a OUTRA conta.
        //
        // Nota Java: "existingAccount" é reatribuída lá em cima (linha ~133), então
        // ela NÃO é "effectively final" - e uma lambda só pode capturar variáveis
        // locais finais ou effectively final (a JVM precisa garantir que o valor não
        // muda depois que a lambda "guardou" a referência). Por isso criamos essa
        // cópia (accountBeingUpdated), que nunca é reatribuída, só para uso dentro
        // do lambda abaixo.
        AccountEntity accountBeingUpdated = existingAccount;
        if (!isBlank(accountRequest.externalId())) {
            accountRepository.findByExternalId(accountRequest.externalId()).ifPresent(other -> {
                boolean belongsToAnotherAccount = accountBeingUpdated == null || !other.getId().equals(accountBeingUpdated.getId());
                if (belongsToAnotherAccount) {
                    throw BusinessException.conflict("account.externalId is already registered for another account");
                }
            });
        }

        if (accountRequest.document() != null) {
            validateDocuments(accountRequest.document());
        }
        if (accountRequest.address() != null) {
            validateAddresses(accountRequest.address());
        }
        if (accountRequest.phone() != null) {
            validatePhones(accountRequest.phone());
        }

        // Campos acrescentados em 17/09/2026 (ver README, seção "Evoluções
        // pedidas"): diferente de name/externalId, estes DOIS são obrigatórios
        // em TODA requisição, independente de account.id ter vindo preenchido
        // ou não - por isso ficam fora do bloco "isNewAccount" acima.
        requireNotBlank(accountRequest.email(), "account.email");
        if (accountRequest.isAuthorizedFallback() == null) {
            throw BusinessException.badRequest("account.isAuthorizedFallback is required");
        }

        return existingAccount;
    }

    private void validateDocuments(List<DocumentRequest> documents) {
        for (DocumentRequest document : documents) {
            if (document.type() == null) {
                // Um valor fora do enum DocumentType já teria sido rejeitado pelo Jackson
                // antes de chegar aqui (ver GlobalExceptionHandler); um valor omitido chega como null.
                throw BusinessException.badRequest("account.document.type is required");
            }
            if (isBlank(document.value())) {
                throw BusinessException.badRequest("account.document.value is required");
            }
            if (isBlank(document.country())) {
                throw BusinessException.badRequest("account.document.country is required");
            }
            validateCountry(document.country());
        }
    }

    private void validateAddresses(List<AddressRequest> addresses) {
        for (AddressRequest address : addresses) {
            if (address.type() == null) {
                throw BusinessException.badRequest("account.address.type is required");
            }
            requireNotBlank(address.description(), "account.address.description");
            requireNotBlank(address.addressName(), "account.address.addressName");
            requireNotBlank(address.number(), "account.address.number");
            requireNotBlank(address.zipCode(), "account.address.zipCode");
            requireNotBlank(address.country(), "account.address.country");
            validateCountry(address.country());
        }
    }

    private void validatePhones(List<PhoneRequest> phones) {
        for (PhoneRequest phone : phones) {
            if (isBlank(phone.number()) || !phone.number().matches("\\d+")) {
                throw BusinessException.badRequest("account.phone.number is required and must contain digits only");
            }
        }
    }

    // ------------------------------------------------------------------
    // Produtos
    // ------------------------------------------------------------------

    private void validateProducts(List<ProductRequest> products) {
        Set<String> codeIds = new HashSet<>();
        for (ProductRequest product : products) {
            requireNotBlank(product.codeId(), "product.codeId");
            if (!codeIds.add(product.codeId())) {
                throw BusinessException.badRequest("product.codeId must be unique within the request: " + product.codeId());
            }
            requireNotBlank(product.name(), "product.name");
            if (product.type() == null) {
                throw BusinessException.badRequest("product.type is required");
            }
            if (product.isExpiriationService() == null) {
                throw BusinessException.badRequest("product.isExpiriationService is required");
            }
            if (product.type() == ProductType.RECURRENCE && !product.isExpiriationService()) {
                throw BusinessException.badRequest("product.isExpiriationService cannot be false when product.type is RECURRENCE");
            }
            if (product.type() == ProductType.RECURRENCE && product.recurrenceFrequency() == null) {
                throw BusinessException.badRequest("product.recurrenceFrequency is required when product.type is RECURRENCE");
            }

            if (product.productValue() == null || MoneyUtil.isNegative(product.productValue())) {
                throw BusinessException.badRequest("product.productValue is required and must not be negative");
            }
            if (product.discountValue() == null || MoneyUtil.isNegative(product.discountValue())) {
                throw BusinessException.badRequest("product.discountValue is required and must not be negative");
            }
            if (product.discountValue().compareTo(product.productValue()) > 0) {
                throw BusinessException.badRequest("product.discountValue cannot be greater than product.productValue");
            }
            validateDiscountCycles(product);

            requireNotBlank(product.currency(), "product.currency");
            validateCurrency(product.currency());

            if (product.isTrial() == null) {
                throw BusinessException.badRequest("product.isTrial is required");
            }
            if (product.isTrial()) {
                if (product.trialDays() == null || product.trialDays() <= 0) {
                    throw BusinessException.badRequest("product.trialDays is required and must be greater than zero when product.isTrial is true");
                }
            }
        }
    }

    private void validateDiscountCycles(ProductRequest product) {
        boolean hasDiscount = product.discountValue().compareTo(BigDecimal.ZERO) > 0;
        if (!hasDiscount) {
            return;
        }
        if (product.discountCycles() == null || product.discountCycles() < 0) {
            throw BusinessException.badRequest("product.discountCycles is required and must not be negative when product.discountValue is greater than zero");
        }
        // "O valor mínimo é 1 para os descontos na modalidade RECURRENCE"
        if (product.type() == ProductType.RECURRENCE && product.discountCycles() < 1) {
            throw BusinessException.badRequest("product.discountCycles must be at least 1 for RECURRENCE products with a discount");
        }
    }

    // ------------------------------------------------------------------
    // Pagamentos
    // ------------------------------------------------------------------

    private void validatePayments(List<PaymentRequest> payments, List<ProductRequest> products, List<BillingRequest> billings) {
        boolean paymentRequired = hasEffectiveCharge(billings)
                || products.stream().anyMatch(p -> p.type() == ProductType.RECURRENCE);

        if (payments == null || payments.isEmpty()) {
            if (paymentRequired) {
                throw BusinessException.badRequest("payment is required when there is a charge or a RECURRENCE product");
            }
            return;
        }

        int defaultCount = 0;
        for (PaymentRequest payment : payments) {
            if (payment.method() == null) {
                throw BusinessException.badRequest("payment.method is required");
            }
            if (payment.method().isCardBased()) {
                requireNotBlank(payment.cardNumber(), "payment.cardNumber");
                requireNotBlank(payment.expiration(), "payment.expiration");
                if (payment.isMultiple() == null) {
                    throw BusinessException.badRequest("payment.isMultiple is required for CREDIT/DEBIT methods");
                }
                validateExpiration(payment.expiration());
                // Campo acrescentado em 17/09/2026 (ver README, seção "Evoluções
                // pedidas"): obrigatório apenas para CREDIT/DEBIT. Um valor fora do
                // enum CardBrand (ex: "brand": "DINERS") já é rejeitado pelo Jackson
                // antes de chegar aqui - ver GlobalExceptionHandler.handleMalformedJson
                // - exatamente o mesmo tratamento já aplicado a payment.method e
                // product.type. Aqui só resta checar a AUSÊNCIA do campo.
                if (payment.brand() == null) {
                    throw BusinessException.badRequest("payment.brand is required for CREDIT/DEBIT methods");
                }
            }
            if (payment.isDefault() == null) {
                throw BusinessException.badRequest("payment.isDefault is required");
            }
            if (payment.isDefault()) {
                defaultCount++;
            }
            validateInstallments(payment.installments(), payment.method(), null);
            validateTokens(payment.token());
        }
        if (defaultCount != 1) {
            throw BusinessException.badRequest("exactly one payment method must have isDefault = true");
        }
    }

    private void validateExpiration(String expiration) {
        YearMonth yearMonth = CardExpirationUtil.parse(expiration)
                .orElseThrow(() -> BusinessException.badRequest("payment.expiration must be in MM/YY format with a valid month (e.g. 03/31)"));
        if (CardExpirationUtil.isExpired(yearMonth, LocalDate.now(com.aalvarenga.billing.util.EpochDateUtil.BUSINESS_ZONE))) {
            throw BusinessException.badRequest("payment.expiration refers to an already expired card");
        }
    }

    private void validateInstallments(Integer installments, PaymentMethod method, RecurrenceFrequency relatedProductFrequency) {
        if (installments == null || installments <= 0) {
            throw BusinessException.badRequest("installments is required and must be greater than zero");
        }
        boolean forcedSingleInstallment = !method.allowsInstallments() || relatedProductFrequency == RecurrenceFrequency.MONTH;
        if (forcedSingleInstallment && installments != 1) {
            throw BusinessException.badRequest("installments must be 1 for method " + method
                    + (relatedProductFrequency == RecurrenceFrequency.MONTH ? " with a MONTH recurrence product" : ""));
        }
        if (!forcedSingleInstallment && (installments < 1 || installments > 12)) {
            throw BusinessException.badRequest("installments must be between 1 and 12 for method " + method);
        }
    }

    private void validateTokens(List<TokenRequest> tokens) {
        if (tokens == null) {
            return;
        }
        for (TokenRequest token : tokens) {
            requireNotBlank(token.name(), "payment.token.name");
            requireNotBlank(token.id(), "payment.token.id");
            requireNotBlank(token.gateway(), "payment.token.gateway");
            if (token.expirationDate() != null && !token.expirationDate().isBlank()) {
                try {
                    Long.parseLong(token.expirationDate());
                } catch (NumberFormatException ex) {
                    throw BusinessException.badRequest("payment.token.expirationDate must be a valid epoch milliseconds value");
                }
                // NOTA (ver README/ANALISE.md): o enunciado reaproveita, aqui, o mesmo texto
                // usado para a checagem de "não pode estar no futuro" de transactionDate, o
                // que não faria sentido para uma data de EXPIRAÇÃO de token. Interpretamos
                // isso como um erro de cópia no enunciado original e não aplicamos essa
                // restrição a este campo.
            }
            if (paymentTokenRepository.existsByToken(token.id())) {
                throw BusinessException.conflict("payment.token.id is already registered: " + token.id());
            }
        }
    }

    // ------------------------------------------------------------------
    // Faturamento (billing)
    // ------------------------------------------------------------------

    private void validateBillings(List<BillingRequest> billings, List<ProductRequest> products, List<PaymentRequest> payments) {
        if (billings == null) {
            return;
        }
        for (BillingRequest billing : billings) {
            requireNotBlank(billing.codeId(), "billing.codeId");
            ProductRequest relatedProduct = PurchaseLookupUtils.findProductByCodeId(products, billing.codeId())
                    .orElseThrow(() -> BusinessException.badRequest("billing.codeId does not match any product.codeId: " + billing.codeId()));

            requireNonNegative(billing.productValue(), "billing.productValue");
            requireNonNegative(billing.discountValue(), "billing.discountValue");
            requireNonNegative(billing.taxValue(), "billing.taxValue");
            requireNonNegative(billing.chargedValue(), "billing.chargedValue");

            BigDecimal taxSum = billing.tax() == null ? BigDecimal.ZERO
                    : billing.tax().stream().map(t -> t.value() == null ? BigDecimal.ZERO : t.value())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!MoneyUtil.equalsMoney(taxSum, billing.taxValue())) {
                throw BusinessException.preconditionFailed("The sum of taxes does not match on product " + billing.codeId());
            }

            BigDecimal expectedCharged = billing.productValue()
                    .subtract(billing.discountValue())
                    .add(billing.taxValue());
            if (!MoneyUtil.equalsMoney(expectedCharged, billing.chargedValue())) {
                throw BusinessException.preconditionFailed(
                        "productValue - discountValue + taxValue does not match chargedValue on product " + billing.codeId());
            }

            requireNotBlank(billing.currency(), "billing.currency");
            validateCurrency(billing.currency());

            requireNotBlank(billing.transactionId(), "billing.transactionId");
            if (billRepository.existsByTransactionId(billing.transactionId())) {
                throw BusinessException.conflict("billing.transactionId is already registered: " + billing.transactionId());
            }

            requireNotBlank(billing.provider(), "billing.provider");
            if (billing.paymentMethod() == null) {
                throw BusinessException.badRequest("billing.paymentMethod is required");
            }
            if (payments != null && !payments.isEmpty()
                    && PurchaseLookupUtils.findPaymentByMethod(payments, billing.paymentMethod()).isEmpty()) {
                throw BusinessException.badRequest("billing.paymentMethod does not match any informed payment.method: " + billing.paymentMethod());
            }

            RecurrenceFrequency relatedFrequency = relatedProduct.type() == ProductType.RECURRENCE
                    ? relatedProduct.recurrenceFrequency() : null;
            validateInstallments(billing.installments(), billing.paymentMethod(), relatedFrequency);

            // Regra de trial confirmada com o usuário (ver README/ANALISE.md, pergunta 2):
            // billing pode existir para um produto trial, desde que chargedValue = 0.
            if (Boolean.TRUE.equals(relatedProduct.isTrial()) && !MoneyUtil.isZero(billing.chargedValue())) {
                throw BusinessException.preconditionFailed(
                        "billing.chargedValue must be zero for a trial product: " + billing.codeId());
            }
        }
    }

    private boolean hasEffectiveCharge(List<BillingRequest> billings) {
        return PurchaseLookupUtils.sumChargedValues(billings).compareTo(BigDecimal.ZERO) > 0;
    }

    // ------------------------------------------------------------------
    // Helpers genéricos
    // ------------------------------------------------------------------

    private void validateCountry(String code) {
        if (!countryDomainRepository.existsById(code)) {
            throw BusinessException.badRequest("Unknown country code: " + code);
        }
    }

    private void validateCurrency(String code) {
        if (!currencyDomainRepository.existsById(code)) {
            throw BusinessException.badRequest("Unknown currency code: " + code);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireNotBlank(String value, String fieldName) {
        if (isBlank(value)) {
            throw BusinessException.badRequest(fieldName + " is required");
        }
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null || MoneyUtil.isNegative(value)) {
            throw BusinessException.badRequest(fieldName + " is required and must not be negative");
        }
    }

    private static Long parseId(String id, String fieldName) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw BusinessException.badRequest(fieldName + " must be a valid numeric id");
        }
    }
}
