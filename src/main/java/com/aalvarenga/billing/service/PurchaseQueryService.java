package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.PurchaseQueryRequest;
import com.aalvarenga.billing.dto.response.QueryAccountItem;
import com.aalvarenga.billing.dto.response.QueryBillItem;
import com.aalvarenga.billing.dto.response.QueryDiscountItem;
import com.aalvarenga.billing.dto.response.QueryDocumentItem;
import com.aalvarenga.billing.dto.response.QueryPaymentItem;
import com.aalvarenga.billing.dto.response.QueryPhoneItem;
import com.aalvarenga.billing.dto.response.QueryProductItem;
import com.aalvarenga.billing.dto.response.QueryResponse;
import com.aalvarenga.billing.dto.response.QueryTaxItem;
import com.aalvarenga.billing.entity.AccountDocumentEntity;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.BillTaxEntity;
import com.aalvarenga.billing.entity.DiscountEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.exception.BusinessException;
import com.aalvarenga.billing.repository.AccountDocumentRepository;
import com.aalvarenga.billing.repository.AccountPhoneRepository;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.BillTaxRepository;
import com.aalvarenga.billing.repository.DiscountRepository;
import com.aalvarenga.billing.repository.PaymentRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import com.aalvarenga.billing.util.AssetIdFormatter;
import com.aalvarenga.billing.util.EpochDateUtil;
import com.aalvarenga.billing.util.MaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fachada do caso de uso "consultar dados já persistidos" -
 * {@code POST /api/v1/purchases/query}. Mesmo racional de {@link PurchaseService}
 * para a criação de compra: único ponto de entrada chamado pelo controller,
 * decide a ordem (validar/resolver -> montar resposta -> logar, sempre,
 * mesmo em erro) e nunca deixa uma exceção "vazar" sem virar uma resposta
 * HTTP.
 *
 * <p>Documento amplo de decisões de design tomadas para esta feature (via
 * pergunta de múltipla escolha ao usuário, ou por dedução a partir do
 * schema já existente) fica no doc de decisões do projeto - aqui, cada
 * regra tem um comentário curto apontando POR QUE ela é implementada assim.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseQueryService {

    private static final int DOCUMENT_VISIBLE_DIGITS = 2;
    private static final int CARD_NUMBER_VISIBLE_DIGITS = 4;

    private final PurchaseQueryValidationService purchaseQueryValidationService;
    private final AccountDocumentRepository accountDocumentRepository;
    private final AccountPhoneRepository accountPhoneRepository;
    private final ProductRepository productRepository;
    private final DiscountRepository discountRepository;
    private final PaymentRepository paymentRepository;
    private final BillRepository billRepository;
    private final BillTaxRepository billTaxRepository;
    private final DomainStatusLookupService domainStatusLookupService;
    private final RecurrenceCalculatorService recurrenceCalculatorService;
    private final InstallmentSplitService installmentSplitService;
    private final RequestLogService requestLogService;
    private final JsonMapper objectMapper;

    public ResponseEntity<QueryResponse> process(PurchaseQueryRequest request) {
        String inputJson = toJsonSafely(request);
        QueryResponse response;
        HttpStatus status;

        try {
            ValidatedQueryContext context = purchaseQueryValidationService.validate(request);
            response = buildSuccessResponse(context);
            status = HttpStatus.OK;
        } catch (BusinessException businessException) {
            response = QueryResponse.error(String.valueOf(businessException.getStatus().value()), businessException.getMessage());
            status = businessException.getStatus();
        } catch (Exception unexpected) {
            log.error("Unexpected error while processing purchase query externalId={} productId={}",
                    request.externalId(), request.productId(), unexpected);
            response = QueryResponse.error("500", "Unexpected internal error");
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String outputJson = toJsonSafely(response);
        // T_LOG.PROTOCOL é NOT NULL, mas a consulta não tem um "protocol" de
        // verdade (esse campo só existe na criação de compra) - sintetizamos
        // uma chave só para fins de auditoria/rastreio, reaproveitando a
        // mesma tabela/serviço de log em vez de criar uma tabela paralela só
        // para este novo endpoint (mesma instrução do enunciado original:
        // "registra as requisições do serviço em tabela", sem distinguir
        // por endpoint).
        String logKey = "QUERY externalId=" + request.externalId() + " productId=" + request.productId();
        requestLogService.log(logKey, response.result().name(), response.code(), response.reason(), inputJson, outputJson);

        return ResponseEntity.status(status).body(response);
    }

    private QueryResponse buildSuccessResponse(ValidatedQueryContext context) {
        AccountEntity account = context.account();
        long now = EpochDateUtil.nowMillis();

        // Produtos-alvo: SEMPRE resolvidos (baratos - no máximo 1 SELECT),
        // porque payment/bill precisam do conjunto de productIds mesmo
        // quando "returnProductData=false" (essa flag só controla se a
        // estrutura "products" aparece na SAÍDA, não o escopo da consulta).
        List<ProductEntity> targetProducts = context.productFilter() != null
                ? List.of(context.productFilter())
                : productRepository.findByAccountId(account.getId());
        List<Long> targetProductIds = targetProducts.stream().map(ProductEntity::getId).toList();

        List<QueryAccountItem> accountItems = List.of(buildAccountItem(account));
        List<QueryProductItem> productItems = context.returnProductData()
                ? emptyToNull(buildProductItems(targetProducts, now))
                : null;
        List<QueryPaymentItem> paymentItems = context.returnPaymentData()
                ? emptyToNull(buildPaymentItems(account, context.productFilter()))
                : null;
        List<QueryBillItem> billItems = context.returnBillData()
                ? emptyToNull(buildBillItems(targetProductIds, context.maxBillReturn()))
                : null;

        return QueryResponse.success(accountItems, productItems, paymentItems, billItems);
    }

    // ------------------------------------------------------------------
    // account
    // ------------------------------------------------------------------

    private QueryAccountItem buildAccountItem(AccountEntity account) {
        String accountSt = domainStatusLookupService.accountStatuses(Set.of(account.getStatus())).get(account.getStatus());

        List<QueryDocumentItem> documents = accountDocumentRepository
                .findByAccountIdAndStatus(account.getId(), DomainStatus.ACTIVE).stream()
                .map(this::toDocumentItem)
                .toList();
        List<QueryPhoneItem> phones = accountPhoneRepository
                .findByAccountIdAndStatus(account.getId(), DomainStatus.ACTIVE).stream()
                .map(phone -> new QueryPhoneItem(phone.getNumber()))
                .toList();

        return new QueryAccountItem(
                account.getExternalId(),
                AssetIdFormatter.account(account.getId()),
                String.valueOf(account.getCreatedDt()),
                account.getEmail(),
                accountSt,
                account.getAuthorizedFallback(),
                emptyToNull(documents),
                emptyToNull(phones));
    }

    private QueryDocumentItem toDocumentItem(AccountDocumentEntity document) {
        // Regra do anexo: mostrar só os 2 últimos dígitos; se o valor tiver
        // 4 posições ou menos, mascarar por inteiro - exatamente o que
        // MaskingUtil.maskKeepingLast já faz para qualquer visibleSuffixLength.
        return new QueryDocumentItem(document.getType(), document.getDescription(),
                MaskingUtil.maskKeepingLast(document.getValue(), DOCUMENT_VISIBLE_DIGITS));
    }

    // ------------------------------------------------------------------
    // products
    // ------------------------------------------------------------------

    private List<QueryProductItem> buildProductItems(List<ProductEntity> products, long now) {
        if (products.isEmpty()) {
            return List.of();
        }
        Set<Integer> statusIds = products.stream().map(ProductEntity::getStatus).collect(Collectors.toSet());
        Map<Integer, String> productStatusByStatusId = domainStatusLookupService.productStatuses(statusIds);

        // Acrescentados em 21/09/2026 (ver README, seção "Evoluções pedidas") -
        // mesmo padrão de lote (1 SELECT por tabela de domínio envolvida, nunca
        // 1 por produto) já usado acima para productStatuses.
        Set<Integer> suspensionStatusIds = products.stream().map(ProductEntity::getSuspensionStatus).collect(Collectors.toSet());
        Map<Integer, String> suspensionStatusById = domainStatusLookupService.productSuspensionStatuses(suspensionStatusIds);
        Set<Integer> cancellationStatusIds = products.stream().map(ProductEntity::getCancellationStatus).collect(Collectors.toSet());
        Map<Integer, String> cancellationStatusById = domainStatusLookupService.productCancellationStatuses(cancellationStatusIds);

        List<Long> productIds = products.stream().map(ProductEntity::getId).toList();
        Map<Long, List<DiscountEntity>> discountsByProductId = discountRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(DiscountEntity::getProductId));
        Set<Integer> discountStatusIds = discountsByProductId.values().stream()
                .flatMap(List::stream).map(DiscountEntity::getStatus).collect(Collectors.toSet());
        Map<Integer, String> discountStatusById = domainStatusLookupService.discountStatuses(discountStatusIds);

        return products.stream()
                .map(product -> toProductItem(product, discountsByProductId.getOrDefault(product.getId(), List.of()),
                        discountStatusById, productStatusByStatusId.get(product.getStatus()), now,
                        suspensionStatusById.get(product.getSuspensionStatus()),
                        cancellationStatusById.get(product.getCancellationStatus())))
                .toList();
    }

    private QueryProductItem toProductItem(ProductEntity product, List<DiscountEntity> productDiscounts,
                                            Map<Integer, String> discountStatusById, String productSt, long now,
                                            String suspensionSt, String cancellationSt) {
        // Desconto "vigente" (para a lista products[].discount): status
        // ativo E ainda não expirado em relação ao INSTANTE ATUAL - decisão
        // tomada com o usuário diante de uma contradição no anexo original
        // (ver doc de decisões do projeto e QueryDiscountItem).
        List<QueryDiscountItem> activeDiscounts = productDiscounts.stream()
                .filter(discount -> discount.getStatus() == DomainStatus.DISCOUNT_ACTIVE && discount.getEndDt() > now)
                .map(discount -> toDiscountItem(discount, discountStatusById.get(discount.getStatus())))
                .toList();

        BigDecimal nextBillValue = calculateNextBillValue(product, productDiscounts);
        boolean isActiveTrial = calculateIsActiveTrial(product, now);

        String defaultPaymentId = product.getDefaultPaymentId() != null
                ? AssetIdFormatter.payment(Long.parseLong(product.getDefaultPaymentId()))
                : null;

        return new QueryProductItem(
                AssetIdFormatter.product(product.getId()),
                product.getChannel(),
                product.getProductId(),
                product.getName(),
                product.getAssetId(),
                String.valueOf(product.getCreatedDt()),
                String.valueOf(product.getTransactionDt()),
                product.getType(),
                product.getExpirationService(),
                product.getTrial(),
                product.getTrialDays(),
                String.valueOf(product.getCycleStartDt()),
                nullableToString(product.getCycleEndDt()),
                nullableToString(product.getNextBillDt()),
                product.getRecurrenceFrequency(),
                product.getCurrency(),
                product.getValue(),
                isActiveTrial,
                defaultPaymentId,
                productSt,
                nullableToString(product.getCancellationReqDt()),
                nullableToString(product.getCancellationSchDt()),
                nextBillValue,
                emptyToNull(activeDiscounts),
                suspensionSt,
                product.getCancellationChannel(),
                nullableToString(product.getCancellationEfcDt()),
                cancellationSt,
                product.getCancellationDescription(),
                product.getAutoCancelSch());
    }

    private QueryDiscountItem toDiscountItem(DiscountEntity discount, String discountSt) {
        return new QueryDiscountItem(
                AssetIdFormatter.discount(discount.getId()),
                String.valueOf(discount.getCreatedDt()),
                String.valueOf(discount.getStartDt()),
                String.valueOf(discount.getEndDt()),
                discount.getValue(),
                discountSt);
    }

    /**
     * {@code value} do produto menos os descontos que ainda estarão
     * vigentes na data da PRÓXIMA cobrança - regra explícita do anexo:
     * "considerar o endDt(discount) maior que o nextBillDt(product)". Note
     * que é uma comparação DIFERENTE da usada para a lista
     * {@code products[].discount} (que compara com o instante ATUAL, não
     * com {@code nextBillDt}) - são duas regras distintas no mesmo
     * documento, para duas saídas distintas.
     */
    private BigDecimal calculateNextBillValue(ProductEntity product, List<DiscountEntity> productDiscounts) {
        if (product.getNextBillDt() == null) {
            return null;
        }
        BigDecimal totalDiscount = productDiscounts.stream()
                .filter(discount -> discount.getStatus() == DomainStatus.DISCOUNT_ACTIVE && discount.getEndDt() > product.getNextBillDt())
                .map(DiscountEntity::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return product.getValue().subtract(totalDiscount);
    }

    /**
     * Reaproveita {@link RecurrenceCalculatorService#calculateTrialEndDate}
     * (já usado na criação da compra para calcular a próxima cobrança
     * pós-trial) - é EXATAMENTE a mesma conta que o anexo desta consulta
     * descreve para {@code isActiveTrial} ("transactionDt + trialDays,
     * jogado para a meia-noite do dia seguinte"), então não há necessidade
     * de duplicar a lógica de cálculo de data aqui.
     */
    private boolean calculateIsActiveTrial(ProductEntity product, long now) {
        if (!Boolean.TRUE.equals(product.getTrial()) || product.getTrialDays() == null) {
            return false;
        }
        long trialEndEpochMillis = recurrenceCalculatorService.calculateTrialEndDate(product.getTransactionDt(), product.getTrialDays());
        return trialEndEpochMillis > now;
    }

    // ------------------------------------------------------------------
    // payment
    // ------------------------------------------------------------------

    private List<QueryPaymentItem> buildPaymentItems(AccountEntity account, ProductEntity productFilter) {
        List<PaymentEntity> payments;
        if (productFilter != null) {
            // Não existe FK direta de pagamento para produto no schema atual
            // - o único vínculo explícito é T_PRODUCT.DEFAULT_PAYMENT_ID.
            // Por isso, quando a consulta é por productId, "os registros de
            // pagamento associados ao productId" só pode significar o
            // pagamento padrão do produto (ver doc de decisões do projeto).
            payments = resolveDefaultPayment(productFilter);
        } else {
            payments = paymentRepository.findByAccountIdAndStatus(account.getId(), DomainStatus.PAYMENT_ACTIVE);
        }

        Set<Integer> statusIds = payments.stream().map(PaymentEntity::getStatus).collect(Collectors.toSet());
        Map<Integer, String> paymentStatusById = domainStatusLookupService.paymentStatuses(statusIds);

        return payments.stream().map(payment -> toPaymentItem(payment, paymentStatusById.get(payment.getStatus()))).toList();
    }

    private List<PaymentEntity> resolveDefaultPayment(ProductEntity productFilter) {
        if (productFilter.getDefaultPaymentId() == null) {
            return List.of();
        }
        return paymentRepository.findById(Long.parseLong(productFilter.getDefaultPaymentId()))
                .filter(payment -> payment.getStatus() == DomainStatus.PAYMENT_ACTIVE)
                .map(List::of)
                .orElseGet(List::of);
    }

    private QueryPaymentItem toPaymentItem(PaymentEntity payment, String paymentSt) {
        return new QueryPaymentItem(
                AssetIdFormatter.payment(payment.getId()),
                String.valueOf(payment.getCreatedDt()),
                payment.getMethod(),
                // Acrescentado em 21/09/2026 a pedido do usuário - simples
                // repasse de T_PAYMENT.BRAND, sem nenhuma regra: já vem
                // null para PIX/WALLET desde a persistência (ver
                // PurchaseValidationService.validatePayments), então não há
                // necessidade de tratamento extra aqui.
                payment.getBrand(),
                payment.getIssuer(),
                MaskingUtil.maskKeepingLast(payment.getCardNumber(), CARD_NUMBER_VISIBLE_DIGITS),
                payment.getExpiration(),
                payment.getMultiple(),
                payment.getDefaultMethod(),
                Integer.parseInt(payment.getInstallments()),
                paymentSt);
    }

    // ------------------------------------------------------------------
    // bill
    // ------------------------------------------------------------------

    private List<QueryBillItem> buildBillItems(List<Long> productIds, int maxBillReturn) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        // Já vem ordenado desc por DUE_DT (ver BillRepository) - regra
        // explícita do anexo ("considerar sempre os registros em modo
        // decrescente"). maxBillReturn <= 0 significa "sem limite".
        List<BillEntity> bills = billRepository.findByProductIdInOrderByDueDtDesc(productIds);
        if (maxBillReturn > 0 && bills.size() > maxBillReturn) {
            bills = bills.subList(0, maxBillReturn);
        }
        if (bills.isEmpty()) {
            return List.of();
        }

        List<Long> billIds = bills.stream().map(BillEntity::getId).toList();
        Map<Long, List<QueryTaxItem>> taxesByBillId = billTaxRepository.findByBillIdIn(billIds).stream()
                .collect(Collectors.groupingBy(BillTaxEntity::getBillId,
                        Collectors.mapping(tax -> new QueryTaxItem(tax.getName(), tax.getValue()), Collectors.toList())));

        Set<Integer> billStatusIds = bills.stream().map(BillEntity::getStatus).collect(Collectors.toSet());
        Map<Integer, String> billStatusById = domainStatusLookupService.billStatuses(billStatusIds);
        Set<Integer> billTypeIds = bills.stream().map(BillEntity::getBillType).collect(Collectors.toSet());
        Map<Integer, String> billTypeById = domainStatusLookupService.billTypes(billTypeIds);
        // Acrescentado em 21/09/2026 (ver README, seção "Evoluções pedidas").
        Set<Integer> refundStatusIds = bills.stream().map(BillEntity::getRefundStatus).collect(Collectors.toSet());
        Map<Integer, String> refundStatusById = domainStatusLookupService.billRefundStatuses(refundStatusIds);

        return bills.stream()
                .map(bill -> toBillItem(bill, taxesByBillId.getOrDefault(bill.getId(), List.of()),
                        billStatusById.get(bill.getStatus()), billTypeById.get(bill.getBillType()),
                        refundStatusById.get(bill.getRefundStatus())))
                .toList();
    }

    private QueryBillItem toBillItem(BillEntity bill, List<QueryTaxItem> taxes, String billSt, String billType, String refundSt) {
        int installments = Integer.parseInt(bill.getInstallments());
        BigDecimal splitValue = installmentSplitService.baseInstallmentValue(bill.getChargedValue(), installments);
        // "chargedValue - refundValue" - ver javadoc de QueryBillItem.updatedValue.
        BigDecimal updatedValue = bill.getChargedValue().subtract(bill.getRefundValue());

        return new QueryBillItem(
                AssetIdFormatter.billing(bill.getId()),
                String.valueOf(bill.getCreatedDt()),
                String.valueOf(bill.getCycleStartDt()),
                nullableToString(bill.getCycleEndDt()),
                String.valueOf(bill.getDueDt()),
                AssetIdFormatter.product(bill.getProductId()),
                bill.getProductValue(),
                bill.getDiscountValue(),
                bill.getTaxValue(),
                bill.getChargedValue(),
                installments,
                splitValue,
                bill.getCurrency(),
                bill.getProvider(),
                bill.getPaymentMethod(),
                billSt,
                billType,
                emptyToNull(taxes.stream().sorted(Comparator.comparing(QueryTaxItem::name)).toList()),
                bill.getBalanceValue(),
                bill.getRefundValue(),
                updatedValue,
                refundSt);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private String nullableToString(Long epochMillis) {
        return epochMillis == null ? null : String.valueOf(epochMillis);
    }

    private <T> List<T> emptyToNull(List<T> list) {
        return list.isEmpty() ? null : list;
    }

    private String toJsonSafely(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return "<unable to serialize: " + e.getMessage() + ">";
        }
    }
}
