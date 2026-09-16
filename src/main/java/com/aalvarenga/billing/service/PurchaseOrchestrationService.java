package com.aalvarenga.billing.service;

import com.aalvarenga.billing.dto.request.ProductRequest;
import com.aalvarenga.billing.dto.request.PurchaseRequest;
import com.aalvarenga.billing.dto.response.AccountResponseItem;
import com.aalvarenga.billing.dto.response.BillingResponseItem;
import com.aalvarenga.billing.dto.response.PaymentResponseItem;
import com.aalvarenga.billing.dto.response.ProductResponseItem;
import com.aalvarenga.billing.dto.response.PurchaseResponse;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.BillEntity;
import com.aalvarenga.billing.entity.PaymentEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.enums.PaymentMethod;
import com.aalvarenga.billing.util.AssetIdFormatter;
import com.aalvarenga.billing.util.PurchaseLookupUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Orquestra a PERSISTÊNCIA de uma compra já validada: grava conta, produtos,
 * pagamentos e faturas dentro de uma ÚNICA transação de banco (tudo ou nada)
 * e monta a resposta de sucesso.
 *
 * <p>Este service pressupõe que {@link PurchaseValidationService#validate}
 * já rodou com sucesso - ele não faz mais nenhuma validação de negócio,
 * apenas grava dados e traduz entidades para os DTOs de resposta.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrchestrationService {

    private final AccountService accountService;
    private final PaymentService paymentService;
    private final ProductService productService;
    private final BillingService billingService;

    /**
     * A anotação {@code @Transactional} aqui é o que garante atomicidade: se
     * qualquer INSERT falhar no meio do caminho (ex: uma constraint do banco
     * que escapou da validação em memória), TUDO o que já tinha sido gravado
     * nesta mesma chamada é desfeito (rollback) - nunca fica uma compra
     * "pela metade".
     */
    @Transactional
    public PurchaseResponse persistAndBuildResponse(PurchaseRequest request, ValidatedPurchaseContext context) {
        AccountEntity account = accountService.resolveAccount(request.account().getFirst(), context.existingAccount());
        accountService.upsertDocuments(request.account().getFirst().document(), account.getId());
        accountService.insertAddresses(request.account().getFirst().address(), account.getId());
        accountService.insertPhones(request.account().getFirst().phone(), account.getId());

        Map<PaymentMethod, PaymentEntity> payments = paymentService.persistPayments(request.payment(), account.getId());
        PaymentEntity defaultPayment = payments.values().stream()
                .filter(p -> Boolean.TRUE.equals(p.getDefaultMethod()))
                .findFirst()
                .orElse(null);

        Map<String, ProductEntity> products = productService.persistProducts(
                request.product(), context.transactionDateEpochMillis(), request.channel(), defaultPayment, account.getId());

        List<BillEntity> bills = billingService.persistBillings(request.billing(), products, payments);

        return buildSuccessResponse(request, account, products, payments, bills);
    }

    private PurchaseResponse buildSuccessResponse(PurchaseRequest request,
                                                   AccountEntity account,
                                                   Map<String, ProductEntity> products,
                                                   Map<PaymentMethod, PaymentEntity> payments,
                                                   List<BillEntity> bills) {
        List<AccountResponseItem> accountItems = List.of(new AccountResponseItem(AssetIdFormatter.account(account.getId())));

        List<ProductResponseItem> productItems = request.product().stream()
                .map(ProductRequest::codeId)
                .map(products::get)
                .map(this::toProductResponseItem)
                .toList();

        boolean hasEffectiveCharge = PurchaseLookupUtils.sumChargedValues(request.billing()).compareTo(BigDecimal.ZERO) > 0;

        List<PaymentResponseItem> paymentItems = (!hasEffectiveCharge || payments.isEmpty())
                ? null
                : payments.values().stream().map(this::toPaymentResponseItem).toList();

        List<BillingResponseItem> billingItems = bills.isEmpty()
                ? null
                : bills.stream().map(bill -> toBillingResponseItem(bill, products)).toList();

        return PurchaseResponse.success(request.protocol(), accountItems, productItems, paymentItems, billingItems);
    }

    private ProductResponseItem toProductResponseItem(ProductEntity product) {
        return new ProductResponseItem(
                product.getProductId(),
                AssetIdFormatter.product(product.getId()),
                product.getNextBillDt() != null ? String.valueOf(product.getNextBillDt()) : null,
                product.getCycleEndDt() != null ? String.valueOf(product.getCycleEndDt()) : null,
                product.getTrial(),
                AssetIdFormatter.payment(product.getDefaultPaymentId())
        );
    }

    private PaymentResponseItem toPaymentResponseItem(PaymentEntity payment) {
        return new PaymentResponseItem(payment.getMethod(), payment.getDefaultMethod(), AssetIdFormatter.payment(payment.getId()));
    }

    private BillingResponseItem toBillingResponseItem(BillEntity bill, Map<String, ProductEntity> products) {
        ProductEntity product = products.get(bill.getCodeId());
        return new BillingResponseItem(
                bill.getCodeId(),
                product != null ? product.getName() : null,
                bill.getPaymentMethod(),
                bill.getChargedValue(),
                bill.getCurrency(),
                AssetIdFormatter.billing(bill.getId())
        );
    }
}
